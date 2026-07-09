package top.colorgarden.pulseaudiojava;

import top.colorgarden.pulseaudiojava.audio.ChannelMap;
import top.colorgarden.pulseaudiojava.audio.SampleFormat;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;
import top.colorgarden.pulseaudiojava.audio.SinkInfo;
import top.colorgarden.pulseaudiojava.audio.SourceInfo;
import top.colorgarden.pulseaudiojava.auth.CookieReader;
import top.colorgarden.pulseaudiojava.playback.AudioPlayer;
import top.colorgarden.pulseaudiojava.protocol.*;
import top.colorgarden.pulseaudiojava.stream.PlaybackStream;
import top.colorgarden.pulseaudiojava.stream.RecordStream;
import top.colorgarden.pulseaudiojava.stream.StreamContext;
import top.colorgarden.pulseaudiojava.transport.TcpTransport;
import top.colorgarden.pulseaudiojava.transport.Transport;
import top.colorgarden.pulseaudiojava.transport.UnixSocketTransport;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure Java PulseAudio client.
 *
 * Usage:
 * <pre>
 *   PulseAudioClient client = PulseAudioClient.connectToDefaultServer();
 *   // authenticate + set up streams...
 *   client.close();
 * </pre>
 */
public class PulseAudioClient implements Closeable {
    private static final String CLIENT_NAME = "PulseAudioJava";

    // Debug flag: set via System.setProperty("pulseaudio.debug","true") or --debug argument
    public static boolean DEBUG = Boolean.getBoolean("pulseaudio.debug");

    /** Enable/disable debug output. */
    public static void setDebug(boolean debug) { DEBUG = debug; }
    public static boolean isDebug() { return DEBUG; }

    private final Transport transport;
    private FrameWriter frameWriter;
    private FrameReader frameReader;
    private Thread ioThread;
    private volatile boolean running;

    // Tag-based reply tracking
    private final AtomicInteger nextTag = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, CompletableFuture<Frame>> pendingReplies = new ConcurrentHashMap<>();

    // Stream management
    private final ConcurrentHashMap<Integer, PlaybackStream> playbackStreams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, RecordStream> recordStreams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, RecordStream> recordStreamsByChannel = new ConcurrentHashMap<>();

    // Debug stats
    private long totalFrames;
    private long totalMemblock;
    private long lastDebug;

    // Server info
    private int serverProtocolVersion;

    /** Effective protocol version = min(client_advertised, server_supported) */
    private int negotiatedVersion() {
        return Math.min(ProtocolConstants.PROTOCOL_VERSION, serverProtocolVersion);
    }

    public PulseAudioClient(Transport transport) {
        this.transport = transport;
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    /**
     * Auto-detect the PulseAudio server and create a client.
     * Does NOT connect yet — call {@link #handshake()} to connect and authenticate.
     *
     * Detection order:
     *   1. $PULSE_SERVER env var (tcp:host:port, unix:/path, or bare host:port)
     *   2. Windows: TCP localhost:4713 (PulseAudio for Windows default)
     *   3. Linux/macOS: XDG_RUNTIME_DIR/pulse/native → /run/user/$UID/pulse/native
     */
    public static PulseAudioClient create() throws IOException {
        // 1. PULSE_SERVER environment variable (works on all platforms)
        String pulseServer = System.getenv("PULSE_SERVER");
        if (pulseServer != null) pulseServer = pulseServer.trim();
        if (pulseServer != null && !pulseServer.isEmpty()) {
            return new PulseAudioClient(parsePulseServer(pulseServer));
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (isWindows) {
            // Windows: PulseAudio uses TCP by default
            return new PulseAudioClient(
                    new TcpTransport("127.0.0.1", ProtocolConstants.DEFAULT_PORT));
        }

        // Linux/macOS: try Unix sockets
        String xdgRuntime = System.getenv("XDG_RUNTIME_DIR");
        if (xdgRuntime != null) {
            String socketPath = xdgRuntime + "/pulse/native";
            if (Files.exists(Paths.get(socketPath))) {
                return new PulseAudioClient(new UnixSocketTransport(socketPath));
            }
        }

        try {
            String uid = System.getProperty("user.name");
            String[] possiblePaths = {
                "/run/user/" + uid + "/pulse/native",
                System.getProperty("user.home") + "/.pulse/native",
                "/tmp/pulse-" + uid + "/native",
            };
            for (String path : possiblePaths) {
                if (Files.exists(Paths.get(path))) {
                    return new PulseAudioClient(new UnixSocketTransport(path));
                }
            }
        } catch (Exception ignored) {}

        // Last resort: TCP localhost
        return new PulseAudioClient(new TcpTransport("127.0.0.1", ProtocolConstants.DEFAULT_PORT));
    }

    /**
     * Parse a PULSE_SERVER value into a Transport.
     * Handles: tcp:host:port, tcp4:host:port, tcp6:host:port,
     *          unix:/path/to/socket, or bare host:port.
     */
    private static Transport parsePulseServer(String server) {
        if (server.startsWith("tcp:") || server.startsWith("tcp4:") || server.startsWith("tcp6:")) {
            String addr = server.substring(server.indexOf(':') + 1);
            String[] parts = addr.split(":");
            String host = parts[0].trim();
            int port = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : ProtocolConstants.DEFAULT_PORT;
            return new TcpTransport(host, port);
        } else if (server.startsWith("unix:")) {
            return new UnixSocketTransport(server.substring(5));
        } else if (server.contains(":") && !server.startsWith("/")) {
            // Assume bare host:port
            String[] parts = server.split(":");
            return new TcpTransport(parts[0], Integer.parseInt(parts[1]));
        } else {
            // Assume Unix socket path
            return new UnixSocketTransport(server);
        }
    }

    /**
     * Quick connect: TCP host + port + cookie file path. Handles everything.
     * Example: PulseAudioClient.connect("192.168.3.9", 4713, "D:\\pulse-cookie")
     */
    public static PulseAudioClient connect(String host, int port, String cookiePath)
            throws IOException, PulseAudioException {
        PulseAudioClient client = new PulseAudioClient(new TcpTransport(host, port));
        client.handshake(Paths.get(cookiePath));
        return client;
    }

    // ========================================================================
    // Connection Lifecycle
    // ========================================================================

    /**
     * Establish the transport connection and start the I/O thread.
     */
    public void connect() throws IOException {
        transport.connect();
        frameReader = new FrameReader(transport.getInputStream());
        frameWriter = new FrameWriter(transport.getOutputStream());
        running = true;

        ioThread = new Thread(this::ioLoop, "PulseAudio-IO");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    /**
     * Authenticate with the PulseAudio server using the given cookie.
     * @return the server's protocol version
     */
    public int authenticate(byte[] cookie) throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();

        // Build AUTH payload: [U32:cmd=8] [U32:tag] [U32:version] [ARBITRARY:256-byte-cookie]
        // Order verified from PulseAudio source: context.c writes version BEFORE cookie.
        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(ProtocolConstants.COMMAND_AUTH, tag);
        w.writeU32(ProtocolConstants.PROTOCOL_VERSION);
        w.writeArbitrary(cookie);
        byte[] payload = w.toByteArray();

        CompletableFuture<Frame> future = new CompletableFuture<>();
        pendingReplies.put(tag, future);

        frameWriter.writeControlPacket(payload);

        Frame reply;
        try {
            reply = future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingReplies.remove(tag);
            throw new PulseAudioException("Authentication timed out", e);
        }

        // Reply format: [U32:reply_cmd] [U32:tag] [U32:server_version]
        TagStructReader reader = new TagStructReader(reply.payload);
        long replyCmd = reader.readU32();
        long replyTag = reader.readU32();

        if (replyCmd == ProtocolConstants.COMMAND_ERROR) {
            long errorCode = reader.readU32();
            pendingReplies.remove(tag);
            throw new PulseAudioException("Authentication failed", (int) errorCode);
        }

        if (replyCmd != ProtocolConstants.COMMAND_REPLY) {
            pendingReplies.remove(tag);
            throw new PulseAudioException("Unexpected auth reply command: " + replyCmd);
        }

        serverProtocolVersion = (int) reader.readU32();
        pendingReplies.remove(tag);
        return serverProtocolVersion;
    }

    /**
     * Set the client application name.
     */
    public void setClientName(String name) throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();

        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(ProtocolConstants.COMMAND_SET_CLIENT_NAME, tag);
        // v13+: send proplist with application.name, NOT a plain string
        if (negotiatedVersion() >= 13) {
            TagStructWriter propWriter = new TagStructWriter();
            propWriter.writePropList(java.util.Map.of(
                "application.name", name.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ));
            w.writeRawBytes(propWriter.toByteArray());
        } else {
            w.writeString(name);
        }
        byte[] payload = w.toByteArray();

        CompletableFuture<Frame> future = new CompletableFuture<>();
        pendingReplies.put(tag, future);
        frameWriter.writeControlPacket(payload);

        try {
            Frame reply = future.get(10, TimeUnit.SECONDS);
            TagStructReader reader = new TagStructReader(reply.payload);
            long cmd = reader.readU32(); // command first in reply
            reader.readU32(); // tag
            if (cmd == ProtocolConstants.COMMAND_ERROR) {
                long err = reader.readU32();
                throw new PulseAudioException("setClientName failed", (int) err);
            }
        } catch (java.util.concurrent.TimeoutException e) {
            throw new PulseAudioException("setClientName timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PulseAudioException("setClientName interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new PulseAudioException("setClientName failed", e.getCause());
        } finally {
            pendingReplies.remove(tag);
        }
    }

    /**
     * Connect, authenticate with cookie from default location, set client name.
     */
    public void handshake() throws IOException, PulseAudioException {
        connect();
        byte[] cookie = CookieReader.readDefaultCookie();
        serverProtocolVersion = authenticate(cookie);
        setClientName(CLIENT_NAME);
    }

    /** Connect, authenticate with cookie from specific file, set client name. */
    public void handshake(java.nio.file.Path cookiePath) throws IOException, PulseAudioException {
        connect();
        byte[] cookie = CookieReader.readCookie(cookiePath);
        serverProtocolVersion = authenticate(cookie);
        setClientName(CLIENT_NAME);
    }

    /** Connect, authenticate with raw cookie data, set client name. */
    public void handshake(byte[] cookieBytes) throws IOException, PulseAudioException {
        connect();
        serverProtocolVersion = authenticate(cookieBytes);
        setClientName(CLIENT_NAME);
    }

    // ========================================================================
    // Stream Creation
    // ========================================================================

    /**
     * Create a record stream that captures audio from a source.
     *
     * @param sourceName PulseAudio source name (e.g., "@DEFAULT_SOURCE@" or a monitor source)
     * @param streamName application-level stream name
     * @param spec desired sample spec (server may negotiate different)
     * @param map desired channel map
     * @return the created RecordStream
     */
    public RecordStream createRecordStream(String sourceName, String streamName,
                                            SampleSpec spec, ChannelMap map)
            throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();

        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(ProtocolConstants.COMMAND_CREATE_RECORD_STREAM, tag);

        w.writeSampleSpec(spec);
        w.writeChannelMap(map);

        // source_index: PA_INVALID_INDEX to use source_name
        w.writeU32(ProtocolConstants.INVALID_INDEX);
        // source_name
        if (sourceName != null && !sourceName.isEmpty()) {
            w.writeString(sourceName);
        } else {
            w.writeString("@DEFAULT_SOURCE@");
        }

        // Buffer attrs: limit server-side buffer to 100ms to prevent drift accumulation
        int maxBuf = spec.bytesPerSecond() / 10; // 100ms
        w.writeU32(maxBuf);                      // maxlength
        w.writeBoolean(false);                   // corked
        w.writeU32(maxBuf / 4);                  // fragsize = 25ms chunks

        // v12+ flags
        if (negotiatedVersion() >= 12) {
            w.writeBoolean(false); // no_remap
            w.writeBoolean(false); // no_remix
            w.writeBoolean(false); // fix_format
            w.writeBoolean(false); // fix_rate
            w.writeBoolean(false); // fix_channels
            w.writeBoolean(false); // no_move
            w.writeBoolean(false); // variable_rate
        }

        // v13+: peak_detect, adjust_latency, proplist, direct_on_input_idx
        if (negotiatedVersion() >= 13) {
            w.writeBoolean(false); // peak_detect
            w.writeBoolean(false); // adjust_latency

            // Proplist (MUST be last field in this sequence for v13)
            TagStructWriter pw = new TagStructWriter();
            String name = (streamName != null) ? streamName : "PulseAudioJava Record";
            pw.writePropList(Map.of(
                "application.name", name.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "media.role", "production".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ));
            w.writeRawBytes(pw.toByteArray());

            w.writeU32(ProtocolConstants.INVALID_INDEX); // direct_on_input_idx
        }

        // v14+: early_requests
        if (negotiatedVersion() >= 14) {
            w.writeBoolean(false); // early_requests
        }

        // v15+: dont_inhibit_auto_suspend, fail_on_suspend
        if (negotiatedVersion() >= 15) {
            w.writeBoolean(false); // dont_inhibit_auto_suspend
            w.writeBoolean(false); // fail_on_suspend
        }

        byte[] payload = w.toByteArray();
        CompletableFuture<Frame> future = new CompletableFuture<>();
        pendingReplies.put(tag, future);
        frameWriter.writeControlPacket(payload);

        try {
            Frame reply = future.get(10, TimeUnit.SECONDS);
            StreamContext ctx = parseCreateStreamReply(reply, spec, map);
            RecordStream stream = new RecordStream(this, ctx);
            recordStreams.put(ctx.streamIndex, stream);
            // Data frames use channel 0 for the first stream
            recordStreamsByChannel.put(0, stream);
            recordStreamsByChannel.put(ctx.channel, stream); // also try source_output_index
            return stream;
        } catch (java.util.concurrent.TimeoutException e) {
            throw new PulseAudioException("createRecordStream timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PulseAudioException("createRecordStream interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new PulseAudioException("createRecordStream failed", e.getCause());
        } finally {
            pendingReplies.remove(tag);
        }
    }

    /**
     * Create a playback stream for audio output.
     */
    public PlaybackStream createPlaybackStream(String streamName, SampleSpec spec, ChannelMap map)
            throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();

        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(ProtocolConstants.COMMAND_CREATE_PLAYBACK_STREAM, tag);

        w.writeSampleSpec(spec);
        w.writeChannelMap(map);
        w.writeU32(ProtocolConstants.INVALID_INDEX); // sink_index
        w.writeStringNull();                          // sink_name (null = default sink)
        w.writeU32(0xFFFFFFFFL); // maxlength = -1
        w.writeBoolean(false);   // corked = false
        w.writeU32(0xFFFFFFFFL); // tlength = -1
        w.writeU32(0xFFFFFFFFL); // prebuf = -1
        w.writeU32(0xFFFFFFFFL); // minreq = -1
        w.writeU32(ProtocolConstants.INVALID_INDEX); // syncid
        w.writeCVolumeDefault(spec.channels);

        // v12+ flags
        if (negotiatedVersion() >= 12) {
            w.writeBoolean(false); // no_remap
            w.writeBoolean(false); // no_remix
            w.writeBoolean(false); // fix_format
            w.writeBoolean(false); // fix_rate
            w.writeBoolean(false); // fix_channels
            w.writeBoolean(false); // no_move
            w.writeBoolean(false); // variable_rate
        }

        // v13+: muted, adjust_latency, proplist (MUST be last!)
        if (negotiatedVersion() >= 13) {
            w.writeBoolean(false); // muted
            w.writeBoolean(false); // adjust_latency
            // Proplist at end with media.name
            TagStructWriter pw = new TagStructWriter();
            String name = (streamName != null) ? streamName : "PulseAudioJava Playback";
            pw.writePropList(Map.of(
                "application.name", name.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ));
            w.writeRawBytes(pw.toByteArray());
        }

        if (negotiatedVersion() >= 14) {
            w.writeBoolean(true);  // volume_set
            w.writeBoolean(false); // early_requests
        }

        if (negotiatedVersion() >= 15) {
            w.writeBoolean(false); // muted_set
            w.writeBoolean(false); // dont_inhibit_auto_suspend
            w.writeBoolean(false); // fail_on_suspend
        }

        byte[] payload = w.toByteArray();
        CompletableFuture<Frame> future = new CompletableFuture<>();
        pendingReplies.put(tag, future);
        frameWriter.writeControlPacket(payload);

        try {
            Frame reply = future.get(10, TimeUnit.SECONDS);
            StreamContext ctx = parseCreateStreamReply(reply, spec, map);
            PlaybackStream stream = new PlaybackStream(this, ctx);
            playbackStreams.put(ctx.streamIndex, stream);
            return stream;
        } catch (java.util.concurrent.TimeoutException e) {
            throw new PulseAudioException("createPlaybackStream timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PulseAudioException("createPlaybackStream interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new PulseAudioException("createPlaybackStream failed", e.getCause());
        } finally {
            pendingReplies.remove(tag);
        }
    }

    // ========================================================================
    // Stream Control
    // ========================================================================

    /**
     * Uncork (resume) a record stream.
     */
    public void uncorkRecordStream(RecordStream stream) throws IOException, PulseAudioException {
        sendSimpleCommand(ProtocolConstants.COMMAND_CORK_RECORD_STREAM,
                stream.getStreamIndex(), false);
    }

    /**
     * Uncork (resume) a playback stream.
     */
    public void uncorkPlaybackStream(PlaybackStream stream) throws IOException, PulseAudioException {
        sendSimpleCommand(ProtocolConstants.COMMAND_CORK_PLAYBACK_STREAM,
                stream.getStreamIndex(), false);
    }

    /**
     * Cork (pause) a playback stream.
     */
    public void corkPlaybackStream(PlaybackStream stream) throws IOException, PulseAudioException {
        sendSimpleCommand(ProtocolConstants.COMMAND_CORK_PLAYBACK_STREAM,
                stream.getStreamIndex(), true);
    }

    /**
     * Delete a record stream.
     */
    public void deleteRecordStream(RecordStream stream) throws IOException, PulseAudioException {
        sendStreamDeleteCommand(ProtocolConstants.COMMAND_DELETE_RECORD_STREAM,
                stream.getStreamIndex());
        stream.markClosed();
        recordStreams.remove(stream.getStreamIndex());
        recordStreamsByChannel.remove(stream.getChannel());
    }

    /**
     * Delete a playback stream.
     */
    public void deletePlaybackStream(PlaybackStream stream) throws IOException, PulseAudioException {
        sendStreamDeleteCommand(ProtocolConstants.COMMAND_DELETE_PLAYBACK_STREAM,
                stream.getStreamIndex());
        stream.markClosed();
        playbackStreams.remove(stream.getStreamIndex());
    }

    private void sendSimpleCommand(int command, int streamIndex, boolean cork)
            throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();
        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(command, tag);
        w.writeU32(streamIndex);
        w.writeBoolean(cork); // always write boolean — server reads it unconditionally
        waitForReply(tag, w.toByteArray());
    }

    private void sendStreamDeleteCommand(int command, int streamIndex)
            throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();
        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(command, tag);
        w.writeU32(streamIndex);
        waitForReply(tag, w.toByteArray());
    }

    private void waitForReply(int tag, byte[] payload) throws IOException, PulseAudioException {
        CompletableFuture<Frame> future = new CompletableFuture<>();
        pendingReplies.put(tag, future);
        frameWriter.writeControlPacket(payload);
        try {
            Frame reply = future.get(10, TimeUnit.SECONDS);
            TagStructReader r = new TagStructReader(reply.payload);
            long cmd = r.readU32(); // command first
            r.readU32(); // tag
            if (cmd == ProtocolConstants.COMMAND_ERROR) {
                long err = r.readU32();
                throw new PulseAudioException("Command failed", (int) err);
            }
        } catch (java.util.concurrent.TimeoutException e) {
            throw new PulseAudioException("Command timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PulseAudioException("Command interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new PulseAudioException("Command failed", e.getCause());
        } finally {
            pendingReplies.remove(tag);
        }
    }

    // ========================================================================
    // I/O Loop
    // ========================================================================

    private void ioLoop() {
        while (running) {
            try {
                Frame frame = frameReader.readFrame();
                if (frame == null) {
                    // EOF — server disconnected
                    running = false;
                    failAllPending("Server disconnected");
                    break;
                }
                dispatchFrame(frame);
            } catch (IOException e) {
                if (running) {
                    running = false;
                    failAllPending("IO error: " + e.getMessage());
                }
                break;
            } catch (Exception e) {
                System.err.println("[PulseAudio-IO] Error dispatching frame: " + e.getMessage());
            }
        }
    }

    private void dispatchFrame(Frame frame) {
        if (DEBUG) {
            totalFrames++;
            long now = System.currentTimeMillis();
            if (now - lastDebug > 3000) {
                System.out.printf("[IO] frames=%d memblock=%d control=%b chan=0x%08X len=%d%n",
                        totalFrames, totalMemblock, frame.isControl(), frame.channel, frame.length);
                lastDebug = now;
            }
        }
        if (frame.isControl()) {
            handleControlFrame(frame);
        } else if (frame.isMemblock()) {
            if (DEBUG) totalMemblock++;
            handleDataFrame(frame);
        }
    }

    /**
     * Handle control frames (commands, replies, events from server).
     */
    private void handleControlFrame(Frame frame) {
        TagStructReader reader = new TagStructReader(frame.payload);

        // Every control frame starts with [U32:command] [U32:tag]
        if (!reader.hasMore()) return;

        long command;
        long tag;
        try {
            command = reader.readU32();
            tag = reader.readU32();
        } catch (Exception e) {
            return;
        }

        int cmd = (int) command;

        if (cmd == ProtocolConstants.COMMAND_REPLY || cmd == ProtocolConstants.COMMAND_ERROR) {
            // Reply to a pending request
            CompletableFuture<Frame> future = pendingReplies.remove((int) tag);
            if (future != null) {
                future.complete(frame);
            }
        } else if (cmd == ProtocolConstants.COMMAND_REQUEST) {
            // Server requesting more audio data for a playback stream
            handleRequest(tag, reader);
        } else if (cmd == ProtocolConstants.COMMAND_SUBSCRIBE_EVENT) {
            // Server event notification — ignore for now
        } else if (cmd == ProtocolConstants.COMMAND_PLAYBACK_STREAM_KILLED) {
            // Stream was killed
        } else if (cmd == ProtocolConstants.COMMAND_RECORD_STREAM_KILLED) {
            // Stream was killed
        }
    }

    /**
     * Handle server REQUEST — it wants more audio data.
     * The frame payload after [tag][command] contains the stream index.
     */
    private void handleRequest(long tag, TagStructReader reader) {
        try {
            long streamIndex = reader.readU32();
            PlaybackStream ps = playbackStreams.get((int) streamIndex);
            if (ps != null) {
                ps.handleRequest();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Handle data frames (memblock audio data) by dispatching to the correct record stream.
     */
    private void handleDataFrame(Frame frame) {
        RecordStream stream = recordStreamsByChannel.get(frame.channel);
        if (stream != null) {
            stream.handleDataFrame(frame);
        }
    }

    // ========================================================================
    // Reply Parsing
    // ========================================================================

    /**
     * Parse CREATE_PLAYBACK_STREAM or CREATE_RECORD_STREAM reply.
     */
    private StreamContext parseCreateStreamReply(Frame reply, SampleSpec requestedSpec, ChannelMap requestedMap) {
        TagStructReader reader = new TagStructReader(reply.payload);
        long cmd = reader.readU32(); // command first
        reader.readU32(); // tag

        if (cmd == ProtocolConstants.COMMAND_ERROR) {
            long err = reader.readU32();
            throw new TagStructReader.ProtocolException("Server returned error: " + err);
        }

        // Reply format verified from protocol-native.c command_create_*_stream:
        // [U32:stream_index] [U32:source_output_index]
        // v9+: [U32:maxlength] [U32:tlength] [U32:prebuf] [U32:minreq]
        // v12+: [SAMPLE_SPEC] [CHANNEL_MAP] [U32:device_index] [STRING:device_name] [BOOLEAN:suspended]
        // v13+: [USEC:latency]

        long streamIndex = reader.readU32();
        long sourceOutputIndex = reader.readU32(); // data channel for memblock frames

        // v9+: just maxlength + fragsize (verified from protocol-native.c)
        int maxlength = -1, fragsize = -1;
        if (negotiatedVersion() >= 9) {
            maxlength = (int) reader.readU32();
            fragsize = (int) reader.readU32();
        }

        // v12+: sample_spec, channel_map, device info
        SampleSpec actualSpec = requestedSpec;
        ChannelMap actualMap = requestedMap;
        int deviceIndex = 0;
        String deviceName = "";
        boolean suspended = false;

        if (negotiatedVersion() >= 12) {
            actualSpec = reader.readSampleSpec();
            actualMap = reader.readChannelMap();
            deviceIndex = (int) reader.readU32();
            deviceName = reader.readStringOrNull();
            if (deviceName == null) deviceName = "";
            suspended = reader.readBoolean();
        }

        // v13+: configured latency
        if (negotiatedVersion() >= 13) {
            reader.readUsec(); // skip latency
        }

        return new StreamContext(
                (int) streamIndex, (int) sourceOutputIndex,
                actualSpec, actualMap,
                deviceIndex, deviceName,
                maxlength, -1, -1, -1, fragsize,
                false);
    }

    // ========================================================================
    // API: Device Enumeration
    // ========================================================================

    /** List all sinks (output devices). */
    public java.util.List<SinkInfo> getSinkInfoList() throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();
        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(ProtocolConstants.COMMAND_GET_SINK_INFO_LIST, tag);
        Frame reply = sendAndWait(tag, w.toByteArray());

        TagStructReader r = new TagStructReader(reply.payload);
        r.readU32(); r.readU32(); // reply_cmd, tag
        java.util.List<SinkInfo> sinks = new java.util.ArrayList<>();
        while (r.hasMore()) {
            int idx = (int) r.readU32();
            String name = r.readStringOrNull();
            String desc = r.readStringOrNull();
            SampleSpec ss = r.readSampleSpec();
            ChannelMap cm = r.readChannelMap();
            int module = (int) r.readU32();
            int vol = (int) r.readVolume();
            boolean muted = r.readBoolean();
            int monitor = (int) r.readU32();
            String monName = r.readStringOrNull();
            long latency = r.readUsec();
            int nPorts = 0;
            try { if (r.hasMore()) { r.readU32(); nPorts = (int) r.readU32(); } } catch (Exception ignored) {}
            sinks.add(new SinkInfo(idx, name != null ? name : "", desc != null ? desc : "",
                    ss, cm, module, monitor, monName != null ? monName : "", latency, vol, muted, nPorts));
        }
        return sinks;
    }

    /** List all sources (input/monitor devices). */
    public java.util.List<SourceInfo> getSourceInfoList() throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();
        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(ProtocolConstants.COMMAND_GET_SOURCE_INFO_LIST, tag);
        Frame reply = sendAndWait(tag, w.toByteArray());

        TagStructReader r = new TagStructReader(reply.payload);
        r.readU32(); r.readU32();
        java.util.List<SourceInfo> sources = new java.util.ArrayList<>();
        while (r.hasMore()) {
            int idx = (int) r.readU32();
            String name = r.readStringOrNull();
            String desc = r.readStringOrNull();
            SampleSpec ss = r.readSampleSpec();
            ChannelMap cm = r.readChannelMap();
            int module = (int) r.readU32();
            int vol = (int) r.readVolume();
            boolean muted = r.readBoolean();
            int monitorOf = (int) r.readU32();
            long latency = r.readUsec();
            int nPorts = 0;
            try { if (r.hasMore()) { r.readU32(); nPorts = (int) r.readU32(); } } catch (Exception ignored) {}
            sources.add(new SourceInfo(idx, name != null ? name : "", desc != null ? desc : "",
                    ss, cm, module, monitorOf, latency, vol, muted, nPorts));
        }
        return sources;
    }

    // ========================================================================
    // API: Volume Control
    // ========================================================================

    /** Set sink volume. volume is PA volume value (0..0x10000 = 0%..100%). */
    public void setSinkVolume(int sinkIndex, long volume) throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();
        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(ProtocolConstants.COMMAND_SET_SINK_VOLUME, tag);
        w.writeU32(sinkIndex);
        w.writeVolume(volume);
        sendAndWait(tag, w.toByteArray());
    }

    /** Mute/unmute a sink. */
    public void setSinkMute(int sinkIndex, boolean mute) throws IOException, PulseAudioException {
        int tag = nextTag.getAndIncrement();
        TagStructWriter w = new TagStructWriter();
        w.writeCommandPreamble(ProtocolConstants.COMMAND_SET_SINK_MUTE, tag);
        w.writeU32(sinkIndex);
        w.writeBoolean(mute);
        sendAndWait(tag, w.toByteArray());
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private Frame sendAndWait(int tag, byte[] payload) throws IOException, PulseAudioException {
        CompletableFuture<Frame> future = new CompletableFuture<>();
        pendingReplies.put(tag, future);
        frameWriter.writeControlPacket(payload);
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new PulseAudioException("Command timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PulseAudioException("Command interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new PulseAudioException("Command failed: " + e.getCause().getMessage(), e.getCause());
        } finally {
            pendingReplies.remove(tag);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private void failAllPending(String message) {
        for (CompletableFuture<Frame> f : pendingReplies.values()) {
            f.completeExceptionally(new IOException(message));
        }
        pendingReplies.clear();
    }

    public FrameWriter getFrameWriter() {
        return frameWriter;
    }

    public int getServerProtocolVersion() {
        return serverProtocolVersion;
    }

    public boolean isConnected() {
        return running && transport.isConnected();
    }

    @Override
    public void close() {
        running = false;
        if (ioThread != null) {
            try {
                ioThread.join(2000);
            } catch (InterruptedException ignored) {}
        }
        failAllPending("Client closed");
        try {
            if (frameWriter != null) frameWriter.close();
        } catch (IOException ignored) {}
        try {
            if (frameReader != null) frameReader.close();
        } catch (IOException ignored) {}
        try {
            transport.close();
        } catch (IOException ignored) {}
    }
}

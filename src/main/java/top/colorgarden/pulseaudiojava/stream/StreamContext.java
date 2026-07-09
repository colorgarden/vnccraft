package top.colorgarden.pulseaudiojava.stream;

import top.colorgarden.pulseaudiojava.audio.ChannelMap;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;

/**
 * Holds the server-negotiated stream metadata returned from
 * CREATE_PLAYBACK_STREAM or CREATE_RECORD_STREAM reply.
 */
public class StreamContext {
    /** Server-assigned stream index */
    public final int streamIndex;

    /** Data channel number for memblock frames */
    public final int channel;

    /** Negotiated sample spec (may differ from requested) */
    public final SampleSpec sampleSpec;

    /** Negotiated channel map */
    public final ChannelMap channelMap;

    /** Server-assigned sink/source index */
    public final int deviceIndex;

    /** Server-assigned sink/source name */
    public final String deviceName;

    /** Actual buffer attributes from server */
    public final int maxlength, tlength, prebuf, minreq, fragsize;

    /** Whether the stream starts corked */
    public final boolean corked;

    public StreamContext(int streamIndex, int channel,
                         SampleSpec sampleSpec, ChannelMap channelMap,
                         int deviceIndex, String deviceName,
                         int maxlength, int tlength, int prebuf, int minreq, int fragsize,
                         boolean corked) {
        this.streamIndex = streamIndex;
        this.channel = channel;
        this.sampleSpec = sampleSpec;
        this.channelMap = channelMap;
        this.deviceIndex = deviceIndex;
        this.deviceName = deviceName;
        this.maxlength = maxlength;
        this.tlength = tlength;
        this.prebuf = prebuf;
        this.minreq = minreq;
        this.fragsize = fragsize;
        this.corked = corked;
    }

    @Override
    public String toString() {
        return String.format("StreamContext[idx=%d, chan=%d, %s, corked=%b]",
                streamIndex, channel, sampleSpec, corked);
    }
}

package top.colorgarden.pulseaudiojava.protocol;

/**
 * PulseAudio native protocol constants. All values verified against the official
 * PulseAudio source code (native-common.h, pstream.c, tagstruct.h).
 */
public final class ProtocolConstants {

    private ProtocolConstants() {}

    // ========================================================================
    // Tag Types (tagstruct.h) — each field is preceded by a 1-byte marker
    // ========================================================================
    public static final byte TAG_INVALID       = 0;
    public static final byte TAG_STRING        = 't';   // 0x74, null-terminated UTF-8
    public static final byte TAG_STRING_NULL   = 'N';   // 0x4E, no data
    public static final byte TAG_U32           = 'L';   // 0x4C, 4 bytes big-endian
    public static final byte TAG_U8            = 'B';   // 0x42, 1 byte
    public static final byte TAG_U64           = 'R';   // 0x52, 8 bytes big-endian
    public static final byte TAG_S64           = 'r';   // 0x72, 8 bytes big-endian signed
    public static final byte TAG_SAMPLE_SPEC   = 'a';   // 0x61, fmt(1B)+ch(1B)+rate(4B)
    public static final byte TAG_ARBITRARY     = 'x';   // 0x78, 4B len(BE) + data
    public static final byte TAG_BOOLEAN_TRUE  = '1';   // 0x31, no data
    public static final byte TAG_BOOLEAN_FALSE = '0';   // 0x30, no data
    public static final byte TAG_BOOLEAN       = TAG_BOOLEAN_TRUE;
    public static final byte TAG_TIMEVAL       = 'T';   // 0x54, 8B sec + 8B usec
    public static final byte TAG_USEC          = 'U';   // 0x55, 8 bytes big-endian uint64
    public static final byte TAG_CHANNEL_MAP   = 'm';   // 0x6D, 1B count + pos[]
    public static final byte TAG_CVOLUME       = 'v';   // 0x76, 1B ch + 4B/BE vol[]
    public static final byte TAG_PROPLIST      = 'P';   // 0x50
    public static final byte TAG_VOLUME        = 'V';   // 0x56, 4 bytes big-endian
    public static final byte TAG_FORMAT_INFO   = 'f';   // 0x66

    // ========================================================================
    // Command Codes (native-common.h) — auto-incremented C enum from 0
    // ========================================================================
    public static final int COMMAND_ERROR                          = 0;
    public static final int COMMAND_TIMEOUT                        = 1;
    public static final int COMMAND_REPLY                          = 2;
    public static final int COMMAND_CREATE_PLAYBACK_STREAM         = 3;
    public static final int COMMAND_DELETE_PLAYBACK_STREAM         = 4;
    public static final int COMMAND_CREATE_RECORD_STREAM           = 5;
    public static final int COMMAND_DELETE_RECORD_STREAM           = 6;
    public static final int COMMAND_EXIT                           = 7;
    public static final int COMMAND_AUTH                           = 8;
    public static final int COMMAND_SET_CLIENT_NAME                = 9;
    public static final int COMMAND_LOOKUP_SINK                    = 10;
    public static final int COMMAND_LOOKUP_SOURCE                  = 11;
    public static final int COMMAND_DRAIN_PLAYBACK_STREAM          = 12;
    public static final int COMMAND_STAT                           = 13;
    public static final int COMMAND_GET_PLAYBACK_LATENCY           = 14;
    public static final int COMMAND_CREATE_UPLOAD_STREAM           = 15;
    public static final int COMMAND_DELETE_UPLOAD_STREAM           = 16;
    public static final int COMMAND_FINISH_UPLOAD_STREAM           = 17;
    public static final int COMMAND_PLAY_SAMPLE                    = 18;
    public static final int COMMAND_REMOVE_SAMPLE                  = 19;
    public static final int COMMAND_GET_SERVER_INFO                = 20;
    public static final int COMMAND_GET_SINK_INFO                  = 21;
    public static final int COMMAND_GET_SINK_INFO_LIST             = 22;
    public static final int COMMAND_GET_SOURCE_INFO                = 23;
    public static final int COMMAND_GET_SOURCE_INFO_LIST           = 24;
    public static final int COMMAND_GET_MODULE_INFO                = 25;
    public static final int COMMAND_GET_MODULE_INFO_LIST           = 26;
    public static final int COMMAND_GET_CLIENT_INFO                = 27;
    public static final int COMMAND_GET_CLIENT_INFO_LIST           = 28;
    public static final int COMMAND_GET_SINK_INPUT_INFO            = 29;
    public static final int COMMAND_GET_SINK_INPUT_INFO_LIST       = 30;
    public static final int COMMAND_GET_SOURCE_OUTPUT_INFO         = 31;
    public static final int COMMAND_GET_SOURCE_OUTPUT_INFO_LIST    = 32;
    public static final int COMMAND_GET_SAMPLE_INFO                = 33;
    public static final int COMMAND_GET_SAMPLE_INFO_LIST           = 34;
    public static final int COMMAND_SUBSCRIBE                      = 35;
    public static final int COMMAND_SET_SINK_VOLUME                = 36;
    public static final int COMMAND_SET_SINK_INPUT_VOLUME          = 37;
    public static final int COMMAND_SET_SOURCE_VOLUME              = 38;
    public static final int COMMAND_SET_SINK_MUTE                  = 39;
    public static final int COMMAND_SET_SOURCE_MUTE                = 40;
    public static final int COMMAND_CORK_PLAYBACK_STREAM           = 41;
    public static final int COMMAND_FLUSH_PLAYBACK_STREAM          = 42;
    public static final int COMMAND_TRIGGER_PLAYBACK_STREAM        = 43;
    public static final int COMMAND_SET_DEFAULT_SINK               = 44;
    public static final int COMMAND_SET_DEFAULT_SOURCE             = 45;
    public static final int COMMAND_SET_PLAYBACK_STREAM_NAME       = 46;
    public static final int COMMAND_SET_RECORD_STREAM_NAME         = 47;
    public static final int COMMAND_KILL_CLIENT                    = 48;
    public static final int COMMAND_KILL_SINK_INPUT                = 49;
    public static final int COMMAND_KILL_SOURCE_OUTPUT             = 50;
    public static final int COMMAND_LOAD_MODULE                    = 51;
    public static final int COMMAND_UNLOAD_MODULE                  = 52;
    public static final int COMMAND_GET_RECORD_LATENCY             = 57;
    public static final int COMMAND_CORK_RECORD_STREAM             = 58;
    public static final int COMMAND_FLUSH_RECORD_STREAM            = 59;
    public static final int COMMAND_PREBUF_PLAYBACK_STREAM         = 60;

    // Server -> Client
    public static final int COMMAND_REQUEST                        = 61;
    public static final int COMMAND_OVERFLOW                       = 62;
    public static final int COMMAND_UNDERFLOW                      = 63;
    public static final int COMMAND_PLAYBACK_STREAM_KILLED         = 64;
    public static final int COMMAND_RECORD_STREAM_KILLED           = 65;
    public static final int COMMAND_SUBSCRIBE_EVENT                = 66;

    // v10+
    public static final int COMMAND_MOVE_SINK_INPUT                = 67;
    public static final int COMMAND_MOVE_SOURCE_OUTPUT             = 68;
    // v11+
    public static final int COMMAND_SET_SINK_INPUT_MUTE            = 69;
    public static final int COMMAND_SUSPEND_SINK                   = 70;
    public static final int COMMAND_SUSPEND_SOURCE                 = 71;
    // v12+
    public static final int COMMAND_SET_PLAYBACK_STREAM_BUFFER_ATTR    = 72;
    public static final int COMMAND_SET_RECORD_STREAM_BUFFER_ATTR      = 73;
    public static final int COMMAND_UPDATE_PLAYBACK_STREAM_SAMPLE_RATE = 74;
    public static final int COMMAND_UPDATE_RECORD_STREAM_SAMPLE_RATE   = 75;
    // v12 Server->Client
    public static final int COMMAND_PLAYBACK_STREAM_SUSPENDED      = 76;
    public static final int COMMAND_RECORD_STREAM_SUSPENDED        = 77;
    public static final int COMMAND_PLAYBACK_STREAM_MOVED          = 78;
    public static final int COMMAND_RECORD_STREAM_MOVED            = 79;
    // v13+
    public static final int COMMAND_UPDATE_RECORD_STREAM_PROPLIST   = 80;
    public static final int COMMAND_UPDATE_PLAYBACK_STREAM_PROPLIST = 81;
    public static final int COMMAND_UPDATE_CLIENT_PROPLIST          = 82;
    public static final int COMMAND_REMOVE_RECORD_STREAM_PROPLIST   = 83;
    public static final int COMMAND_REMOVE_PLAYBACK_STREAM_PROPLIST = 84;
    public static final int COMMAND_REMOVE_CLIENT_PROPLIST          = 85;
    public static final int COMMAND_STARTED                         = 86;
    // v14+
    public static final int COMMAND_EXTENSION                       = 87;

    // ========================================================================
    // Error Codes (native-common.h)
    // ========================================================================
    public static final int ERROR_OK                    = 0;
    public static final int ERROR_ACCESS                = 1;
    public static final int ERROR_COMMAND               = 2;
    public static final int ERROR_INVALID               = 3;
    public static final int ERROR_EXIST                 = 4;
    public static final int ERROR_NOENTITY              = 5;
    public static final int ERROR_CONNECTIONREFUSED     = 6;
    public static final int ERROR_PROTOCOL              = 7;
    public static final int ERROR_TIMEOUT               = 8;
    public static final int ERROR_AUTHKEY               = 9;
    public static final int ERROR_INTERNAL              = 10;
    public static final int ERROR_CONNECTIONTERMINATED  = 11;
    public static final int ERROR_KILLED                = 12;
    public static final int ERROR_INVALIDSERVER         = 13;

    // ========================================================================
    // PStream Descriptor Constants (pstream.c)
    // ========================================================================
    /** Descriptor size: 5 × uint32 = 20 bytes */
    public static final int PSTREAM_DESCRIPTOR_SIZE = 20;

    /** Minimum buffer for header+small payload = 256 bytes */
    public static final int MINIBUF_SIZE = 256;

    /** Maximum frame payload size = 16 MB */
    public static final int FRAME_SIZE_MAX_ALLOW = 1024 * 1024 * 16;

    // Descriptor indices
    public static final int DESCRIPTOR_LENGTH    = 0;
    public static final int DESCRIPTOR_CHANNEL   = 1;
    public static final int DESCRIPTOR_OFFSET_HI = 2;
    public static final int DESCRIPTOR_OFFSET_LO = 3;
    public static final int DESCRIPTOR_FLAGS     = 4;

    // SHM info indices (4 × uint32_t after descriptor when SHM is used)
    public static final int SHM_BLOCKID = 0;
    public static final int SHM_SHMID   = 1;
    public static final int SHM_INDEX   = 2;
    public static final int SHM_LENGTH  = 3;

    // ========================================================================
    // Flags (pstream.c) — applied to the flags field of the descriptor
    // ========================================================================
    public static final int FLAG_SHMDATA             = 0x80000000;
    public static final int FLAG_SHMDATA_MEMFD_BLOCK = 0x20000000;
    public static final int FLAG_SHMRELEASE          = 0x40000000;
    public static final int FLAG_SHMREVOKE           = 0xC0000000;
    public static final int FLAG_SHMMASK             = 0xFF000000;
    public static final int FLAG_SEEKMASK            = 0x000000FF;
    public static final int FLAG_SHMWRITABLE         = 0x00800000;

    // ========================================================================
    // Seek Mode (def.h)
    // ========================================================================
    public static final int SEEK_RELATIVE       = 0;
    public static final int SEEK_ABSOLUTE       = 1;
    public static final int SEEK_RELATIVE_ON_READ = 2;
    public static final int SEEK_RELATIVE_END   = 3;

    // ========================================================================
    // Special Values
    // ========================================================================
    /** Magic channel value for control/packet frames */
    public static final int CONTROL_CHANNEL = -1;  // 0xFFFFFFFF as signed int32

    /** Invalid index sentinel */
    public static final long INVALID_INDEX = 0xFFFFFFFFL;

    /** Default PulseAudio server TCP port */
    public static final int DEFAULT_PORT = 4713;

    /** Cookie length in bytes */
    public static final int COOKIE_LENGTH = 256;

    /** Protocol version we advertise */
    public static final int PROTOCOL_VERSION = 13;

    // ========================================================================
    // Subscription Mask (def.h)
    // ========================================================================
    public static final int SUBSCRIPTION_MASK_SINK           = 0x0001;
    public static final int SUBSCRIPTION_MASK_SOURCE         = 0x0002;
    public static final int SUBSCRIPTION_MASK_SINK_INPUT     = 0x0004;
    public static final int SUBSCRIPTION_MASK_SOURCE_OUTPUT  = 0x0008;
    public static final int SUBSCRIPTION_MASK_MODULE         = 0x0010;
    public static final int SUBSCRIPTION_MASK_CLIENT         = 0x0020;
    public static final int SUBSCRIPTION_MASK_SAMPLE_CACHE   = 0x0040;
    public static final int SUBSCRIPTION_MASK_SERVER         = 0x0080;
    public static final int SUBSCRIPTION_MASK_CARD           = 0x0400;
    public static final int SUBSCRIPTION_MASK_ALL            = 0x04FF;
}

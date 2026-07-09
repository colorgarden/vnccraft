package top.colorgarden.pulseaudiojava.audio;

/**
 * PulseAudio channel map — defines speaker positions for each channel.
 * Position constants match the PA_CHANNEL_POSITION_* values from pulse/channelmap.h.
 */
public class ChannelMap {
    public final int[] positions;

    public ChannelMap(int[] positions) {
        this.positions = positions.clone();
    }

    public int channelCount() {
        return positions.length;
    }

    // Well-known channel positions matching PA_CHANNEL_POSITION_* enum
    public static final int POS_INVALID        = -1;
    public static final int POS_MONO           = 0;
    public static final int POS_FRONT_LEFT     = 1;
    public static final int POS_FRONT_RIGHT    = 2;
    public static final int POS_FRONT_CENTER   = 3;
    public static final int POS_LFE            = 4;  // subwoofer
    public static final int POS_REAR_LEFT      = 5;
    public static final int POS_REAR_RIGHT     = 6;
    public static final int POS_REAR_CENTER    = 8;
    public static final int POS_SIDE_LEFT      = 9;
    public static final int POS_SIDE_RIGHT     = 10;

    // Factory methods
    public static ChannelMap mono() {
        return new ChannelMap(new int[]{POS_MONO});
    }

    public static ChannelMap stereo() {
        return new ChannelMap(new int[]{POS_FRONT_LEFT, POS_FRONT_RIGHT});
    }

    public static ChannelMap surround51() {
        return new ChannelMap(new int[]{
            POS_FRONT_LEFT, POS_FRONT_RIGHT, POS_FRONT_CENTER,
            POS_LFE, POS_REAR_LEFT, POS_REAR_RIGHT
        });
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < positions.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(positions[i]);
        }
        return sb.append("]").toString();
    }
}

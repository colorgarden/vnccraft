package top.colorgarden.pulseaudiojava.audio;

/** PulseAudio sink (output device) info. */
public class SinkInfo {
    public final int index;
    public final String name;
    public final String description;
    public final SampleSpec sampleSpec;
    public final ChannelMap channelMap;
    public final int ownerModule;
    public final int monitorSource;
    public final String monitorSourceName;
    public final long latency;
    public final int volume; // PA volume, 0..0x10000
    public final boolean muted;
    public final int nPorts;

    public SinkInfo(int index, String name, String desc, SampleSpec ss, ChannelMap cm,
                    int module, int monitor, String monitorName, long latency,
                    int volume, boolean muted, int nPorts) {
        this.index = index; this.name = name; this.description = desc;
        this.sampleSpec = ss; this.channelMap = cm;
        this.ownerModule = module; this.monitorSource = monitor;
        this.monitorSourceName = monitorName; this.latency = latency;
        this.volume = volume; this.muted = muted; this.nPorts = nPorts;
    }

    @Override
    public String toString() {
        return String.format("#%d %s [%s %s]", index, name, sampleSpec, description);
    }
}

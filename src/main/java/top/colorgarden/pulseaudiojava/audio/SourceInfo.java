package top.colorgarden.pulseaudiojava.audio;

/** PulseAudio source (input device) info. */
public class SourceInfo {
    public final int index;
    public final String name;
    public final String description;
    public final SampleSpec sampleSpec;
    public final ChannelMap channelMap;
    public final int ownerModule;
    public final int monitorOfSink;
    public final long latency;
    public final int volume;
    public final boolean muted;
    public final int nPorts;

    public SourceInfo(int index, String name, String desc, SampleSpec ss, ChannelMap cm,
                      int module, int monitorOfSink, long latency,
                      int volume, boolean muted, int nPorts) {
        this.index = index; this.name = name; this.description = desc;
        this.sampleSpec = ss; this.channelMap = cm;
        this.ownerModule = module; this.monitorOfSink = monitorOfSink;
        this.latency = latency; this.volume = volume; this.muted = muted;
        this.nPorts = nPorts;
    }

    /** @return true if this source is a monitor of a sink */
    public boolean isMonitor() { return monitorOfSink != -1; }

    @Override
    public String toString() {
        return String.format("#%d %s [%s %s]", index, name, sampleSpec, description);
    }
}

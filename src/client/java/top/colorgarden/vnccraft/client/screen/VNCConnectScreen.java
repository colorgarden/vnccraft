package top.colorgarden.vnccraft.client.screen;

import top.colorgarden.vnccraft.network.C2SVNCConnectRequestPayload;
import top.colorgarden.vnccraft.network.C2SVNCDisconnectRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class VNCConnectScreen extends Screen {

    private final int screenId;
    private EditBox hostField;
    private EditBox portField;
    private EditBox passwordField;
    private net.minecraft.client.gui.components.Checkbox audioCheckbox;
    private EditBox audioPortField;
    private EditBox cookieField;
    private EditBox volumeField;
    private Button connectButton;
    private Button disconnectButton;
    private Button cancelButton;

    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 20;

    public VNCConnectScreen(int screenId, String savedHost, int savedPort) {
        super(Component.translatable("vnccraft.gui.title", screenId));
        this.screenId = screenId;
        this.savedHost = savedHost;
        this.savedPort = savedPort;
    }

    private final String savedHost;
    private final int savedPort;

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 40;

        y += 20;

        this.hostField = new EditBox(this.font, centerX - FIELD_WIDTH / 2, y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("vnccraft.gui.ip"));
        this.hostField.setHint(Component.translatable("vnccraft.gui.ip_hint"));
        if (!savedHost.isEmpty()) this.hostField.setValue(savedHost);
        this.addRenderableWidget(hostField);

        y += 25;
        this.portField = new EditBox(this.font, centerX - FIELD_WIDTH / 2, y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("vnccraft.gui.port"));
        this.portField.setValue(String.valueOf(savedPort));
        this.addRenderableWidget(portField);

        y += 25;
        this.passwordField = new EditBox(this.font, centerX - FIELD_WIDTH / 2, y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("vnccraft.gui.password"));
        this.passwordField.setHint(Component.translatable("vnccraft.gui.password_hint"));
        this.addRenderableWidget(passwordField);

        y += 25;
        this.audioCheckbox = net.minecraft.client.gui.components.Checkbox.builder(
                Component.translatable("vnccraft.gui.audio_enable"), this.font).pos(centerX - FIELD_WIDTH / 2, y).build();
        this.addRenderableWidget(audioCheckbox);

        y += 25;
        this.audioPortField = new EditBox(this.font, centerX - FIELD_WIDTH / 2, y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("vnccraft.gui.audio_port"));
        this.audioPortField.setValue("4713");
        this.addRenderableWidget(audioPortField);

        y += 25;
        this.cookieField = new EditBox(this.font, centerX - FIELD_WIDTH / 2, y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("vnccraft.gui.audio_cookie"));
        this.cookieField.setHint(Component.translatable("vnccraft.gui.audio_cookie_hint"));
        this.cookieField.setMaxLength(1024);
        this.addRenderableWidget(cookieField);

        y += 25;
        this.volumeField = new EditBox(this.font, centerX - FIELD_WIDTH / 2, y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("vnccraft.gui.audio_volume"));
        this.volumeField.setValue("100");
        this.addRenderableWidget(volumeField);

        y += 30;
        this.connectButton = Button.builder(Component.translatable("vnccraft.gui.connect"), btn -> onConnect())
                .pos(centerX - FIELD_WIDTH / 2, y).size(FIELD_WIDTH, 20).build();
        this.addRenderableWidget(connectButton);

        y += 25;
        this.disconnectButton = Button.builder(Component.translatable("vnccraft.gui.disconnect"), btn -> onDisconnect())
                .pos(centerX - FIELD_WIDTH / 2, y).size(FIELD_WIDTH, 20).build();
        this.addRenderableWidget(disconnectButton);

        y += 25;
        this.cancelButton = Button.builder(Component.translatable("vnccraft.gui.cancel"), btn -> onClose())
                .pos(centerX - FIELD_WIDTH / 2, y).size(FIELD_WIDTH, 20).build();
        this.addRenderableWidget(cancelButton);
    }

    private void onConnect() {
        String host = hostField.getValue().trim();
        if (host.isEmpty()) return;
        int port = parsePort();
        String password = passwordField.getValue();

        int audioPort = audioCheckbox.selected() ? 4713 : 0;
        try { audioPort = audioCheckbox.selected() ? Integer.parseInt(audioPortField.getValue().trim()) : 0; } catch (Exception ignored) {}
        String cookie = cookieField.getValue().trim();

        // Save volume setting
        try { top.colorgarden.vnccraft.client.audio.VNCAudioPlayer.getInstance().setUserVolume(
                Float.parseFloat(volumeField.getValue().trim())); } catch (Exception ignored) {}

        ClientPlayNetworking.send(new C2SVNCConnectRequestPayload(
                new C2SVNCConnectRequestPayload.Data(screenId, host, port, password, 1920, 1080, audioPort, cookie)));
        // Client-side Vernacular will be started automatically when the
        // server broadcasts S2CVNCTunnelReadyPayload after opening the tunnel.
        this.onClose();
    }

    private void onDisconnect() {
        ClientPlayNetworking.send(new C2SVNCDisconnectRequestPayload(screenId));
        top.colorgarden.vnccraft.client.vnc.VNCClientRendererManager.getInstance().disconnectVNC(screenId);
        this.onClose();
    }

    private int parsePort() {
        try { return portField.getValue().isEmpty() ? 5900 : Integer.parseInt(portField.getValue().trim()); }
        catch (NumberFormatException e) { return 5900; }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

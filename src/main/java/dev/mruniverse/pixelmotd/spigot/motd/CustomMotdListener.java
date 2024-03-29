package dev.mruniverse.pixelmotd.spigot.motd;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedServerPing;
import com.google.common.io.Files;
import dev.mruniverse.pixelmotd.global.enums.GuardianFiles;
import dev.mruniverse.pixelmotd.global.enums.MotdPlayersMode;
import dev.mruniverse.pixelmotd.global.enums.MotdSettings;
import dev.mruniverse.pixelmotd.global.enums.MotdType;
import dev.mruniverse.pixelmotd.spigot.PixelMOTD;
import dev.mruniverse.pixelmotd.spigot.utils.WrappedStatus;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@SuppressWarnings("unused")
public class CustomMotdListener extends PacketAdapter {

    private final PixelMOTD plugin;

    private final Random random = new Random();

    private FileConfiguration motds;

    private FileConfiguration whitelist;


    public CustomMotdListener(PixelMOTD plugin, ListenerPriority priority) {
        super(plugin,priority, PacketType.Status.Server.SERVER_INFO);
        motds = plugin.getStorage().getControl(GuardianFiles.MOTDS);
        whitelist = plugin.getStorage().getControl(GuardianFiles.WHITELIST);
        this.plugin = plugin;
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    public void update() {
        motds = plugin.getStorage().getControl(GuardianFiles.MOTDS);
        whitelist = plugin.getStorage().getControl(GuardianFiles.WHITELIST);
    }

    private String getMotd(MotdType motdType) {
        ConfigurationSection section = motds.getConfigurationSection(motdType.getMotdsUsingPath());
        if(section == null) return "E991PX";
        List<String> motdToGet = new ArrayList<>(section.getKeys(false));
        return motdToGet.get(random.nextInt(motdToGet.size()));
    }

    private MotdInformation getCurrentMotd(int currentProtocol,int max,int online) {
        if(whitelist.getBoolean("whitelist.toggle")) {
            return new MotdInformation(plugin.getStorage(),MotdType.WHITELIST,getMotd(MotdType.WHITELIST),max,online);
        }
        FileConfiguration settings = plugin.getStorage().getControl(GuardianFiles.SETTINGS);

        boolean outdatedClientMotd = settings.getBoolean("settings.outdated-client-motd");
        boolean outdatedServerMotd = settings.getBoolean("settings.outdated-server-motd");

        int maxProtocol = plugin.getStorage().getControl(GuardianFiles.SETTINGS).getInt("settings.max-server-protocol");
        int minProtocol = plugin.getStorage().getControl(GuardianFiles.SETTINGS).getInt("settings.min-server-protocol");

        if(!outdatedClientMotd && !outdatedServerMotd || currentProtocol >= minProtocol && currentProtocol <= maxProtocol) {
            return new MotdInformation(plugin.getStorage(),MotdType.NORMAL,getMotd(MotdType.NORMAL),max,online);
        }
        if(maxProtocol < currentProtocol && outdatedServerMotd) {
            return new MotdInformation(plugin.getStorage(),MotdType.OUTDATED_SERVER,getMotd(MotdType.OUTDATED_SERVER),max,online);
        }
        if(minProtocol > currentProtocol && outdatedClientMotd) {
            return new MotdInformation(plugin.getStorage(),MotdType.OUTDATED_CLIENT,getMotd(MotdType.OUTDATED_CLIENT),max,online);
        }
        return new MotdInformation(plugin.getStorage(),MotdType.NORMAL,getMotd(MotdType.NORMAL),max,online);
    }

    public static List<WrappedGameProfile> getHover(List<String> hover) {
        List<WrappedGameProfile> result = new ArrayList<>();
        final UUID id = UUID.fromString("0-0-0-0-0");
        for(String line : hover) {
            result.add(new WrappedGameProfile(id, line));
        }
        return result;
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onPacketSending(final PacketEvent event){
        if(event.isCancelled()) return;
        if(event.getPlayer() == null) return;
        if (event.getPacketType() != PacketType.Status.Server.SERVER_INFO) return;

        WrappedStatus packet = new WrappedStatus(event.getPacket());
        WrappedServerPing ping = packet.getJsonResponse();

        MotdPlayers onlinePlayers, maxPlayers;

        int max = Bukkit.getMaxPlayers();
        int online = Bukkit.getOnlinePlayers().size();

        int protocol;

        protocol = ProtocolLibrary.getProtocolManager().getProtocolVersion(event.getPlayer());

        MotdInformation info = getCurrentMotd(protocol,max,online);

        onlinePlayers = plugin.getLoader().getOnline().get(info.getMotdType());
        maxPlayers = plugin.getLoader().getOnline().get(info.getMotdType());

        if(maxPlayers.isEnabled()) {
            if (maxPlayers.getMode() == MotdPlayersMode.EQUALS) {
                max = online;
            } else {
                max = onlinePlayers.getResult(max);
            }
            ping.setPlayersMaximum(max);
            info.setMax(max);
        }

        if(onlinePlayers.isEnabled()) {
            if (onlinePlayers.getMode() == MotdPlayersMode.EQUALS) {
                online = max;
            } else {
                online = onlinePlayers.getResult(online);
            }
            ping.setPlayersOnline(online);
            info.setOnline(online);
        }

        if(info.getHexStatus() && protocol >= 721) {
            try {
                String motd;
                if(info.getHexAllMotd() != null) {
                    motd = info.getHexAllMotd();
                } else {
                    reportIssue();
                    motd = info.getEmergencyHex();
                }
                ping.setMotD(motd);
            }catch (Throwable throwest) {
                reportIssue();
                plugin.getLogs().error(throwest);
                String motd = info.getEmergencyHex();
                ping.setMotD(motd);
            }
        } else {
            try {
                String motd = info.getAllMotd();
                ping.setMotD(motd);
            }catch (Throwable ignored) {
                plugin.getLogs().error("This error is your fault, you have a bad configuration, to prevent spam of this message fix your motds.yml");
                String motd = info.getEmergencyNormal();
                ping.setMotD(motd);
            }
        }

        if(plugin.getStorage().getControl(GuardianFiles.SETTINGS).getBoolean("settings.icon-system") && motds.getBoolean(info.getMotdType().getPath() + "settings.icon")) {

            File[] icons;
            File motdFolder;

            if(info.getCustomIconStatus()) {
                motdFolder = plugin.getStorage().getIconsFolder(info.getMotdType(),info.getMotdName());
            } else {
                motdFolder = plugin.getStorage().getIconsFolder(info.getMotdType());
            }

            icons = motdFolder.listFiles();
            if (icons != null && icons.length != 0) {

                List<File> validIcons = new ArrayList<>();

                if(info.getCustomIconName().equalsIgnoreCase("RANDOM")) {
                    for (File image : icons) {
                        if (Files.getFileExtension(image.getPath()).equals("png")) {
                            validIcons.add(image);
                        }
                    }

                } else {

                    File finding = new File(motdFolder,info.getCustomIconName());

                    if(!finding.exists()) {
                        plugin.getLogs().error("File " + info.getCustomIconName() + " doesn't exists");
                    } else {

                        if (Files.getFileExtension(finding.getPath()).equals("png")) {
                            validIcons.add(finding);
                        } else {
                            plugin.getLogs().error("This image " + info.getCustomIconName() + " need be (.png) 64x64, this image isn't (.png) format");
                        }
                    }
                }

                if (validIcons.size() != 0) {
                    WrappedServerPing.CompressedImage image = getCompressedImage(validIcons.get(random.nextInt(validIcons.size())));
                    if (image != null) ping.setFavicon(image);
                }

            }
        }

        if(motds.getBoolean(info.getMotdType().getSettings(MotdSettings.CUSTOM_PROTOCOL_TOGGLE))) {
            ping.setVersionName(MotdUtils.getWorlds(motds.getString(info.getMotdType().getSettings(MotdSettings.CUSTOM_PROTOCOL_NAME))));
            if(motds.getBoolean(info.getMotdType().getSettings(MotdSettings.CUSTOM_PROTOCOL_VERSION_TOGGLE))) {
                String value = motds.getString(info.getMotdType().getSettings(MotdSettings.CUSTOM_PROTOCOL_VALUE));
                if(value==null) value = "EQUALS";
                if(value.equalsIgnoreCase("EQUALS") || value.equalsIgnoreCase("SAME")) {
                    ping.setVersionProtocol(protocol);
                    return;
                }
                ping.setVersionProtocol(Integer.parseInt(value));
            }
        }

    }

    private WrappedServerPing.CompressedImage getCompressedImage(File file) {
        try {
            return WrappedServerPing.CompressedImage.fromPng(ImageIO.read(file));
        } catch(Throwable ignored) {
            reportBadImage(file.getPath());
            return null;
        }
    }
    private void reportIssue() {
        plugin.getLogs().error("Can't show HexColors, maybe your bungeecord.jar is outdated? showing 1.16 motd without HexColors");
        plugin.getLogs().error("Or maybe this issue is caused because you have a bad configuration in your motds.yml");
        plugin.getLogs().error("If you are sure than is not your issue please contact developer and send your motds.yml");
        plugin.getLogs().error("And your bungeecord version or info about your proxy to try to replicate that issue.");
        plugin.getLogs().error("To disable this warning please disable 'with-hex' motd.");
        plugin.getLogs().error("Or try update your bungeecord.jar or if you are using a fork try using another fork");
        plugin.getLogs().error("To see if the error is fixed.");
    }
    private void reportBadImage(String filePath) {
        plugin.getLogs().warn("Can't read image: &b" + filePath + "&f. Please check image size: 64x64 or check if the image isn't corrupted.");
    }
}

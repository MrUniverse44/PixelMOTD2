package dev.mruniverse.pixelmotd.global.enums;

public enum ListType {
    WHITELIST,
    BLACKLIST;

    public String getPath() {
        switch (this) {
            case BLACKLIST:
                return "blacklist.";
            default:
            case WHITELIST:
                return "whitelist.";
        }
    }

    public String getName() {
        switch (this) {
            case BLACKLIST:
                return "Blacklist";
            default:
            case WHITELIST:
                return "Whitelist";
        }
    }
}

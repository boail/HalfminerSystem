package de.halfminer.hms.enums;

public enum DataType {

    KILLS           ("kills"),
    DEATHS          ("deaths"),
    KD_RATIO        ("kdratio"),
    TIME_ONLINE     ("timeonline"),
    BLOCKS_PLACED   ("blocksplaced"),
    BLOCKS_BROKEN   ("blocksbroken"),
    VOTES           ("votes"),
    SKILL_LEVEL     ("skilllevel"),
    SKILL_ELO       ("skillelo"),
    SKILL_GROUP     ("skillgroup"),
    MOB_KILLS       ("mobkills"),
    REVENUE         ("revenue"),
    LASTKILL        ("lastkill"),
    LAST_NAME       ("lastname"),
    LAST_NAMES      ("lastnames"),
    NEUTP_USED      ("neutp");

    private final String name;

    DataType(String type) {
        name = type;
    }

    public String toString() {
        return name;
    }
}
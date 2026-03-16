package me.adrian.paintball.game;

import org.bukkit.ChatColor;

public enum GameTeam {
    BLUE(ChatColor.BLUE, "Azul"),
    RED(ChatColor.RED, "Rojo"),
    GREEN(ChatColor.GREEN, "Verde"),
    PINK(ChatColor.LIGHT_PURPLE, "Rosa");

    private final ChatColor color;
    private final String name;

    GameTeam(ChatColor color, String name) {
        this.color = color;
        this.name = name;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getColoredName() {
        return color + name;
    }

    public String getName() {
        return name;
    }
}

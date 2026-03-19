package com.alongiss.Alonias;

public class RoomInfo {
    public String code;
    public String name;
    public boolean locked;
    public int count;
    public int max;
    public String status;

    public RoomInfo(String code, String name, boolean locked, int count, int max, String status) {
        this.code = code;
        this.name = name;
        this.locked = locked;
        this.count = count;
        this.max = max;
        this.status = status;
    }
}

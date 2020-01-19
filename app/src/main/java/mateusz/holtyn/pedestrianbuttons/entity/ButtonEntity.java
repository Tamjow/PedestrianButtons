package mateusz.holtyn.pedestrianbuttons.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ButtonEntity {

    @SerializedName("id")
    @Expose
    private Integer id;
    @SerializedName("location")
    @Expose
    private String location;

    public ButtonEntity() {

    }

    public ButtonEntity(Integer id, String location) {
        this.id = id;
        this.location = location;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "ButtonList [id=" + id + ", location=" + location + "]";
    }

}
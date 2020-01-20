package mateusz.holtyn.pedestrianbuttons.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ButtonList {
    @SerializedName("version")
    @Expose
    private Integer version;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    private List<ButtonEntity> buttonList = null;

    public List<ButtonEntity> getButtonList() {
        return buttonList;
    }

    public void setButtonList(List<ButtonEntity> buttonList) {
        this.buttonList = buttonList;
    }


}
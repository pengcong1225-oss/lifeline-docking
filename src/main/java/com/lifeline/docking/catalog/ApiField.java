package com.lifeline.docking.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 接口字段定义（来自源文档逐页 Schema）。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiField {

    private String name;
    private String type;
    private boolean required;
    private String desc;
    /** 页面预填用的示例值；调试发送前必须人工确认，不要直接发给老平台。 */
    private Object sample;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public Object getSample() { return sample; }
    public void setSample(Object sample) { this.sample = sample; }
}

package com.lifeline.docking.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** docking-api-catalog.json 的顶层结构。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogSnapshot {

    private String source;
    private String generatedAt;
    private List<ApiCatalogEntry> entries = new ArrayList<>();

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public List<ApiCatalogEntry> getEntries() { return entries; }
    public void setEntries(List<ApiCatalogEntry> entries) { this.entries = entries; }
}

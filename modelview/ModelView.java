package modelview;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    private String view;
    private Map<String, Object> data;

    public ModelView(String view) {
        this.view = view;
        this.data = new HashMap<>();
    }

    public ModelView(String view, Map<String, Object> data) {
        this.view = view;
        this.data = data != null ? data : new HashMap<>();
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data != null ? data : new HashMap<>();
    }

    public ModelView addData(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}

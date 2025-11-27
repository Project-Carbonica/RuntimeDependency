package com.example;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

/**
 * Example plugin class.
 * 
 * The RuntimeDependency plugin will inject loader code into this class's
 * static initializer automatically. When this class is first loaded,
 * it will load gson-2.10.1.jar from the "libs" directory.
 */
public class MyPlugin {
    
    public static void main(String[] args) {
        System.out.println("MyPlugin started!");
        
        // This will work because Gson is loaded from libs/ directory
        Gson gson = new Gson();
        
        Map<String, String> data = new HashMap<>();
        data.put("message", "Hello from RuntimeDependency!");
        data.put("status", "success");
        
        String json = gson.toJson(data);
        
        System.out.println("JSON output: " + json);
        System.out.println("MyPlugin completed successfully!");
    }
}

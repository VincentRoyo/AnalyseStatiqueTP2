package org.example.visitor.model;

import java.util.ArrayList;
import java.util.List;

public class MethodInfo {
    public String name;
    public int lineCount;
    public int paramsCount;
    public List<MethodCallInfo> methodCalls = new ArrayList<>();
}

package io.metersphere.api.module;

import lombok.Data;

@Data
public class JvmInfo {
    /**
     * JVM内存的空闲空间为
     */
    private long vmFree;
    /**
     * JVM内存已用的空间为
     */
    private long vmUse;

    private long vmTotal;
    /**
     * JVM总内存空间为
     */
    private long vmMax;

    private int totalThread;

    public JvmInfo() {

    }

    public JvmInfo(long vmTotal, long vmFree, long vmMax, long vmUse, int totalThread) {
        this.vmFree = vmFree;
        this.vmTotal = vmTotal;
        this.vmMax = vmMax;
        this.vmUse = vmUse;
        this.totalThread = totalThread;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("当前JVM内存信息：").append("\n");
        builder.append("当前JVM最大内存：" + this.vmMax + " M").append("\n");
        builder.append("当前JVM占用的总内存：" + this.vmTotal + " M").append("\n");
        builder.append("当前JVM空闲内存为：" + this.vmFree + " M").append("\n");
        builder.append("当前JVM已用内存为：" + this.vmUse + " M").append("\n");
        return builder.toString();
    }
}

package com.bluetrum.fota.manager;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;

public interface DataReader {

    /**
     * 打开文件
     */
    void open() throws IOException;

    /**
     * 获取文件的总大小
     * @return 文件的总大小
     */
    int getSize() throws IOException;

    /**
     * 获取文件的MD5头4个字节
     * @return 文件的MD5头4个字节
     */
    @NonNull
    byte[] getHash() throws IOException;

    /**
     * 读取数据
     * @param srcPos 源偏移量
     * @param dst 目标缓冲区
     * @param dstPos 目标缓冲区偏移量
     * @param length 读取大小
     * @return 返回读取的大小，失败则为-1
     */
    int read(int srcPos, byte[] dst, int dstPos, int length) throws IOException;

    /**
     * 关闭文件
     */
    void close() throws IOException;
}

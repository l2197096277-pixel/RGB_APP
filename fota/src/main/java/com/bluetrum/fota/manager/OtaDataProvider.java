package com.bluetrum.fota.manager;

import java.io.IOException;

/**
 * 提供OTA数据的类。
 */
final class OtaDataProvider {

    private final DataReader dataReader;

    private int dataSize;
    private boolean isCompressedData = false;

    private int blockSize = OtaConstants.UNDEFINED_BLOCK_SIZE; // 不需要分包确认
    private int blockOffset = 0;
    private int packetSize = OtaConstants.DEFAULT_MTU_SIZE - 3;
    private int fileOffset = 0;

    public OtaDataProvider(DataReader dataReader) {
        this.dataReader = dataReader;
    }

    public void open() throws IOException {
        dataReader.open();
        dataSize = dataReader.getSize();
        isCompressedData = checkIfCompressedData();
    }

    public void close() throws IOException {
        fileOffset = 0;
        blockOffset = 0;
        packetSize = OtaConstants.DEFAULT_MTU_SIZE - 3;
        blockSize = OtaConstants.UNDEFINED_BLOCK_SIZE;
        dataReader.close();
    }

    private int getDataSize() {
        return dataSize;
    }

    public boolean isCompressedData() {
        return isCompressedData;
    }

    /**
     * 获取开始升级的地址
     * @return 升级地址
     */
    public int getStartAddress() {
        return fileOffset;
    }

    /**
     * 设置开始升级的地址
     * @param address 升级地址
     */
    public void setStartAddress(int address) {
        fileOffset = address;
    }

    /**
     * 设置块大小，固件接收一个块后会进行校验并做下一步处理，随后会回复状态到App。
     * @param size 块大小
     */
    public void setBlockSize(int size) {
        blockSize = size;
    }

    /**
     * 设置发送一包的最大大小（被MTU大小限制）。
     * @param packetSize 最大包大小
     */
    public void setPacketSize(int packetSize) {
        this.packetSize = packetSize;
    }

    /**
     * 获取升级进度
     * @return 升级进度
     */
    public int getProgress() {
        return fileOffset * 100 / getDataSize();
    }

    /**
     * 块是否已经完成发送
     * @return 阶段是否已完成发送
     */
    public boolean isBlockSendFinish() {
        if (blockSize == OtaConstants.UNDEFINED_BLOCK_SIZE) {
            return isAllDataSent();
        } else {
            return (fileOffset - blockOffset == blockSize) || isAllDataSent();
        }
    }

    /**
     * 是否已经发送完所有数据，即文件指针已到达文件末端。
     * @return 是否已经发送完所有数据
     */
    public boolean isAllDataSent() {
        return fileOffset == getDataSize();
    }



    /* Command Data */

    // 每一个块第一包必须严格按照getStartAddress、getTotalLengthToBeSent和
    // getStartData的顺序使用，因为调用后会有一些值会被改变

    /**
     * 返回需要升级的这个块一共要发送的数据长度
     * @return 需要发送的数据长度
     */
    public int getTotalLengthToBeSent() {
        // data length
        int sendLen;
        // 如果不需要分块发送
        if (blockSize == OtaConstants.UNDEFINED_BLOCK_SIZE) {
            sendLen = getDataSize() - fileOffset;
        }
        // 如果需要分块发送
        else {
            // 剩下还没发送的长度
            int leftLen = getDataSize() - fileOffset;
            sendLen = Math.min(leftLen, blockSize);
            blockOffset = fileOffset;
        }

        return sendLen;
    }

    /**
     * 返回需要升级的这个块头一包中固件数据
     * @param headerSize 已经使用的包头长度，数据长度=包长度-包头长度
     * @return 固件数据
     */
    byte[] getStartData(int headerSize) throws IOException {
        // 限定发送数据的长度
        int dataLen = (blockSize == OtaConstants.UNDEFINED_BLOCK_SIZE) ? getDataSize() : blockSize;
        dataLen = Math.min(dataLen, packetSize - headerSize);
        // 判断整个文件有没有越界
        if (fileOffset + dataLen > getDataSize()) {
            dataLen = getDataSize() - fileOffset;
        }

        byte[] readBuffer = new byte[dataLen];
        // 因为长度都是经过计算的，如果读取返回-1，那说明读取失败，抛出异常
        int readLength =
                dataReader.read(fileOffset, readBuffer, 0, dataLen);
        if (readLength < 0) {
            throw new IOException("read data failed");
        }

        byte[] actualData = new byte[readLength];
        System.arraycopy(readBuffer, 0, actualData, 0, readLength);
        fileOffset += readLength;

        return actualData;
    }

    /**
     * 返回下一包中固件数据
     * @param headerSize 已经使用的包头长度，数据长度=包长度-包头长度
     * @return 固件数据
     */
    byte[] getDataToBeSent(int headerSize) throws IOException {
        // 限定发送数据的长度
        int dataLen = (blockSize == OtaConstants.UNDEFINED_BLOCK_SIZE) ? getDataSize() : blockSize;
        dataLen = Math.min(dataLen, packetSize - headerSize);
        // 如果需要分块发送
        if (blockSize != OtaConstants.UNDEFINED_BLOCK_SIZE) {
            // 判断是否超出blockSize
            if ((fileOffset - blockOffset) + dataLen > blockSize) {
                dataLen = blockSize - (fileOffset - blockOffset);
            }
        }
        // 判断整个文件有没有越界
        if (fileOffset + dataLen > getDataSize()) {
            dataLen = getDataSize() - fileOffset;
        }

        byte[] readBuffer = new byte[dataLen];
        // 因为长度都是经过计算的，如果读取返回-1，那说明读取失败，抛出异常
        int readLength =
                dataReader.read(fileOffset, readBuffer, 0, dataLen);
        if (readLength < 0) {
            throw new IOException("read data failed");
        }

        byte[] actualData = new byte[readLength];
        System.arraycopy(readBuffer, 0, actualData, 0, readLength);
        fileOffset += readLength;

        return actualData;
    }


    /**
     * 获取OTA文件的HASH值，供固件端用来对比文件。
     * @return 返回HASH值字节数组。
     */
    public byte[] getHash() throws IOException {
        return dataReader.getHash();
    }

    /* Private */

    private boolean checkIfCompressedData() throws IOException {
        int HEADER_SIZE = 4;
        byte[] header = new byte[HEADER_SIZE];
        int readLen = dataReader.read(0, header, 0, HEADER_SIZE);
        if (readLen == HEADER_SIZE) {
            return checkHeaderIfCompressedData(header);
        } else {
            return false;
        }
    }

    private boolean checkHeaderIfCompressedData(byte[] data) {
        if (data.length >= 3) {
            // POT
            return data[0] == 'P' && data[1] == 'O' && data[2] == 'T';
        }
        return false;
    }
}

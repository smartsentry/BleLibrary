package com.example.blelibray;

public class MbLite {

    private String strOutFrame="";
    private String strInFrame;
    private byte[] mOutFrame;

    /*  Modbus for Qube Commands  */
    private final byte stationAddress = 1;
    public final byte readCoilStatusResponse = 1;
    public final byte readInputStatusResponse = 2;
    public final byte readHoldingRegistersResponse = 3;
    public final byte readInputRegistersResponse = 4;
    public final byte writeSingleCoilResponse = 5;
    public final byte writeSingleRegResponse = 6;
    public final byte writeMultipleCoilsResponse = 15;
    public final byte writeMultipleRegistersResponse = 16;

    /*  Modbus for Qube Machine States  */
    public final byte regInflatorState = 0;
    public final byte regInflatorError = 1;
    public final byte regCurrentPressureHi = 2;
    public final byte regCurrentPressureLo = 3;
    public final byte regTargetPressureHi = 4;
    public final byte regTargetPressureLo = 5;
    public final byte regOverPressureHi = 6;
    public final byte regFirmwareModelCode = 14;
    public final byte regFirmwareVersion = 15;
    public final byte regBuildNumber = 16;
    public final byte regNitroCommand = 66;



    public final byte coilInflateActive = 1;

    private byte coils[] = new byte[16];
    private byte inputstatus[] = new byte[16];
    private byte inputregs[] = new byte[16];
    private byte holdingregs[] = new byte[16];
    private byte responseFnt;
    private byte slaveaddress;
    private int  holdingregaddress;
    private int  coiladdress;
    private int  checksum;
    private int packetnum;
    private int numpackets;

    /*
       Example frame - read 2 holding registers from address 04
       :01030004000204F2
       : = Start of frame
        01 = Station address (8 bits)
          03 = Read Holding Reg (8 bits)
            0004 = Reg Start address (16 bits) - Target Pressure Register / Set Pressure Register
                0002 = Number of registers to read (16-bits)
                    04 = Number of bytes to read (8 bits!!!!????)
                      F2 = LRC (8 bits, NOTE: based on numeric representation values of not ASCII values)
     */
    private byte[] readRawHoldingRegs(int packet_id, int address, int numRegisters) {
        byte numbytes = (byte) ((byte) (numRegisters << 1) & 0xff);
        byte[] command = new byte[] {(byte) ((packet_id >> 8) & 0xff), (byte) (packet_id & 0xff), 0x00, 0x01, stationAddress, readHoldingRegistersResponse, (byte) ((address/256) & 0xff),
                (byte) (address & 0xff), (byte) ((numRegisters/256) & 0xff), (byte) (numRegisters & 0xff), numbytes};
        return command;
    }

    /* :0101000D0001F0  */
    private byte[]  readRawSingleCoil(int packet_id, int address) {
        int numcoils = 1;
        byte[] command = new byte[] { (byte) ((packet_id >> 8) & 0xff), (byte) (packet_id & 0xff), 0x00, 0x01,
                stationAddress, readCoilStatusResponse, (byte) ((address >> 8) & 0xff), (byte) (address & 0xff), (byte) ((numcoils >> 8) &0xff), (byte) (numcoils & 0xff) };
        return command;
    }

    private byte[]  writeRawHoldingRegs(int packet_id, int address, int data, int numRegisters) {
        byte numbytes = (byte) ((byte) (numRegisters << 1) & 0xff);
        byte[] command = new byte[] { (byte) ((packet_id >> 8) & 0xff), (byte) (packet_id & 0xff), 0x00, 0x01, stationAddress, writeMultipleRegistersResponse, (byte) ((address >> 8) & 0xff), (byte) (address & 0xff), (byte) ((numRegisters >> 8) & 0xff), (byte) (numRegisters & 0xff),
                (byte) (numbytes & 0xff), (byte) ((data >> 24) & 0xff), (byte) ((data >> 16) & 0xff), (byte) ((data >> 8) & 0xff), (byte) (data  & 0xff)};
        return command;
    }

    public byte[] buildRawCurrentPressueFrameRead(int packet_id) {
        byte[] TyrePressureFrame = readRawHoldingRegs(packet_id, regCurrentPressureHi, 0x02);
        return TyrePressureFrame;
    }

    public byte[] buildRawTargetPressureFrameRead(int packet_id) {
        byte[] TyrePressureFrame = readRawHoldingRegs(packet_id, regTargetPressureHi, 0x02);
        return TyrePressureFrame;
    }

    public byte[] buildRawInflatorStateFrameRead(int packet_id) {
        byte[] TyrePressureFrame = readRawHoldingRegs(packet_id, regInflatorState, 0x01);
        return TyrePressureFrame;
    }

    public byte[] buildRawGetFirmwareModelCode(int packet_id) {
        byte[] ModelCode = readRawHoldingRegs(packet_id, regFirmwareModelCode, 0x01);
        return ModelCode;
    }

    public byte[] buildRawGetFirmwareVersion(int packet_id) {
        byte[] FirmwareVersion = readRawHoldingRegs(packet_id, regFirmwareVersion, 0x01);
        return FirmwareVersion;
    }

    public byte[] buildRawGetFirmwareBuildNumber(int packet_id) {
        byte[] BuildNumber = readRawHoldingRegs(packet_id, regFirmwareVersion, 0x01);
        return BuildNumber;
    }
    public byte[] buildRawGetNitroStatus(int packet_id) {
        byte[] BuildNumber = readRawHoldingRegs(packet_id, regNitroCommand, 0x02);
        return BuildNumber;
    }
    public byte[] buildRawInflateStatusFrameRead(int packet_id) {

        byte[] TargetStatusFrame = readRawSingleCoil(packet_id, coilInflateActive);
        return TargetStatusFrame;
    }

    public byte[] buildRawTargetPressureFrameWrite(int packet_id, int val) {
        byte[] TargetPressureFrame = writeRawHoldingRegs(packet_id, regTargetPressureHi, val, 2);
        return TargetPressureFrame;
    }

    public byte[] buildRawNitroCommandFrameWrite(int packet_id, int val) {
        byte[] Nitroframe  = writeRawHoldingRegs(packet_id, regNitroCommand, val, 2);
        return Nitroframe;
    }

    public byte[] buildRawInflateDownFrame(int packet_id) {
        /*  Single coil write - Coil=7,  Val=ON (0xff00), LRC=f4  */
        byte [] DownFrame;
        DownFrame = new byte[] {(byte) ((packet_id >> 8) & 0xff), (byte) (packet_id & 0xff), 0x00, 0x01, 0x01, 0x05, 0x00, 0x07, (byte) 0xff, 0x00, (byte) 0xf4};
        return DownFrame;
    }

    public byte[] buildRawInflateUpFrame(int packet_id) {
        /*  Single coil write - Coil=7,  Val=OFF (0x0000), LRC=f3  */
        byte[] Upframe;
        Upframe = new byte[] {(byte) ((packet_id >> 8) & 0xff), (byte) (packet_id & 0xff), 0x00, 0x01, 0x01, 0x05, 0x00, 0x07, 0x00, 0x00, (byte) 0xF3};
        return Upframe;
    }

    public byte[] buildRawCycleDownFrame(int packet_id) {
        /*  Single coil write - Coil=7,  Val=ON (0xff00), LRC=f4  */
        byte [] DownFrame;
        DownFrame = new byte[] {(byte) ((packet_id >> 8) & 0xff), (byte) (packet_id & 0xff), 0x00, 0x01, 0x01, 0x05, 0x00, 0x08, (byte) 0xff, 0x00, (byte) 0xF3};
        return DownFrame;
    }

    public byte[] buildRawCycleUpFrame(int packet_id) {
        /*  Single coil write - Coil=7,  Val=OFF (0x0000), LRC=f3  */
        byte[] Upframe;
        Upframe = new byte[] {(byte) ((packet_id >> 8) & 0xff), (byte) (packet_id & 0xff), 0x00, 0x01, 0x01, 0x05, 0x00, 0x08, 0x00, 0x00, (byte) 0xF4};
        return Upframe;
    }

    public byte[] buildRawMcp23008Command(int packet_id, int command, int argument ) {
        byte[] CmdFrame;
        CmdFrame = new byte[] {(byte) ((packet_id >> 8) & 0xff), (byte) (packet_id & 0xff), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) (command & 0xff), (byte) (argument & 0xff)};
        return CmdFrame;
    }

    /*  Typical RTU / ASCII format

        Header                      None	: (Colon)
        Slave Address               04	    0 4
        Function	                01  	0 1
        Byte Count	                02	    0 2
        Data (Coils 7...10)         0A	    0 A
        Data (Coils 27...20)        11      1 1
        Error Check Lo	            B3	    LRC (D E)
        Error Check Hi	            50	    None
        Trailer	                    None    CR      LF
        Total Bytes	                7       15

     */
    String parseRawData(byte[] rawdata) {
        int index, bytecount=0, val=0;
        String result;

        packetnum = (rawdata[0]  << 8) + rawdata[1];
        numpackets = (rawdata[2]  << 8) + rawdata[3];
        slaveaddress = rawdata[4];
        responseFnt = rawdata[5];

        switch (responseFnt) {

            case readCoilStatusResponse:                     //  Read Coil State (bit pattern
                bytecount = rawdata[6];
                for (index = 0; index < bytecount; index++) {
                    coils[index] = rawdata[index+7];
                }
                break;

            case readInputStatusResponse:                     //  Read Input State (bit pattern
                bytecount = rawdata[6];
                for (index = 0; index < bytecount; index++) {
                    inputstatus[index] = rawdata[index+7];
                }
                break;

            case readHoldingRegistersResponse:
                bytecount = rawdata[6];
                for (index = 0; index < bytecount; index++) {
                    holdingregs[index] = rawdata[index+7]   ;
                }
                break;

            case readInputRegistersResponse:
                bytecount = rawdata[6];
                for (index = 0; index < bytecount; index++) {
                    inputregs[index] = rawdata[index+7]   ;
                }
                break;

            case writeSingleCoilResponse:
                bytecount = 2;
                coiladdress = (rawdata[6] * 256) + rawdata[7];
                break;

            case writeSingleRegResponse:
                bytecount = 2;
                holdingregaddress = (rawdata[6] * 256) + rawdata[7];
                break;

            case writeMultipleCoilsResponse:
                coiladdress = (rawdata[6] * 256) + rawdata[7];
                bytecount = ((rawdata[8]*256) + rawdata[9]) * 2;
                bytecount = 6;
                break;

            case writeMultipleRegistersResponse:
                holdingregaddress = (rawdata[6] * 256) + rawdata[7];
                bytecount = ((rawdata[8]*256) + rawdata[9]) * 2;
                break;

            default:
                break;
        }

        checksum=0;                                 //  Build the checksum
        for (index = 4; index < rawdata.length; index++) {
            checksum += rawdata[index];
        }
        checksum = (0-checksum) & 0xff;             //  Truncate to 8 bits

        String resp="";
        for (index=0; index < rawdata.length; index++) {
            resp = resp.concat(String.format("%02x",(byte) rawdata[index]));
        }
        return resp;
    }

    public byte getResponseFunction() {
        return responseFnt;
    }

    public byte getSlaveAddress() {
        return (byte) slaveaddress;
    }

    public int getPacketNum() {return packetnum;}

    public int getNumpackets() {return numpackets;}

    public int getChecksum() {return checksum;}

    public int getPressure() {
        int pressure, p0, p1, p2, p3;
        p0 = Byte.toUnsignedInt(holdingregs[0]) << 24;
        p1 = Byte.toUnsignedInt(holdingregs[1]) << 16;
        p2 = Byte.toUnsignedInt(holdingregs[2]) << 8;
        p3 = Byte.toUnsignedInt(holdingregs[3]);
        pressure = p0 + p1 + p2 + p3;
        return pressure;
    }

    public int getInflateStatus() {
        int status, p0, p1, p2, p3;
        p0 = Byte.toUnsignedInt(coils[0]);
        status = p0;
        return status;
    }

    public int getInflatorState() {
        int state, s0, s1, s2, s3;
        s0 = Byte.toUnsignedInt(holdingregs[0]) << 8;
        s1 = Byte.toUnsignedInt(holdingregs[1]);
        state = s0 + s1;
        return state;
    }

}









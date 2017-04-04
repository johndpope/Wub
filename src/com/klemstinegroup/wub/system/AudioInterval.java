package com.klemstinegroup.wub.system;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import com.echonest.api.v4.TimedEvent;

public class AudioInterval implements Serializable {

    //TimedEvent te;
    //public int startBytes;
    //public int lengthBytes;
    public byte[] data;
//    public SegmentSong payloadPlay;
    public SegmentSong payloadPrintout;

    //public int endBytes;
//	int newbytestart;

    public AudioInterval(TimedEvent te, byte[] fullData, int song1, int segmentNum1) {
        double start1 = te.getStart();
        double duration = te.getDuration();
        int startBytes = (int) (start1 * Audio.sampleRate * Audio.frameSize) - (int) (start1 * Audio.sampleRate * Audio.frameSize) % Audio.frameSize;
        double lengthInFrames = duration * Audio.sampleRate;
        int lengthBytes = (int) (lengthInFrames * Audio.frameSize) - (int) (lengthInFrames * Audio.frameSize) % Audio.frameSize;
        //int endBytes = startBytes + lengthBytes;
        data = new byte[lengthBytes];

        if (startBytes + lengthBytes > fullData.length) lengthBytes = fullData.length - startBytes;
        System.arraycopy(fullData, startBytes, data, 0, lengthBytes);
        //System.out.println((startBytes+lengthBytes)+"\t"+fullData.length+"\t"+data.length+"\t"+lengthBytes);
        //this.te = te;
//payloadPlay =new SegmentSong(song1,segmentNum1);
payloadPrintout =new SegmentSong(song1,segmentNum1);
    }

    public AudioInterval(List<TimedEvent> list, byte[] fullData) {
        double start1 = list.get(0).getStart();
        double duration = 0;
        for (TimedEvent t : list) duration += t.duration;
        //double duration = te.getDuration();
        int startBytes = (int) (start1 * Audio.sampleRate * Audio.frameSize) - (int) (start1 * Audio.sampleRate * Audio.frameSize) % Audio.frameSize;
        double lengthInFrames = duration * Audio.sampleRate;
        int lengthBytes = (int) (lengthInFrames * Audio.frameSize) - (int) (lengthInFrames * Audio.frameSize) % Audio.frameSize;
        //int endBytes = startBytes + lengthBytes;
        data = new byte[lengthBytes];

        if (startBytes + lengthBytes > fullData.length) lengthBytes = fullData.length - startBytes;
        System.arraycopy(fullData, startBytes, data, 0, lengthBytes);
    }

    public AudioInterval(byte[] data) {
        this.data = data;
    }

//	public String toString() {
//		return startBytes + ":" + lengthBytes;
//	}

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AudioInterval))
            return false;
        AudioInterval i = (AudioInterval) o;
        return this.hashCode() == i.hashCode() && data.length == i.data.length;
    }

    public AudioInterval[] getMono() {
        AudioInterval left = new AudioInterval(new byte[data.length / 2]);
        AudioInterval right = new AudioInterval(new byte[data.length / 2]);
        for (int i = 0; i < data.length; i += 4) {
            left.data[i / 2] = data[i];
            left.data[i / 2 + 1] = data[i + 1];
            right.data[i / 2] = data[i + 2];
            right.data[i / 2 + 1] = data[i + 3];
        }
        return new AudioInterval[]{left,right};
    }

    public void makeStereo(AudioInterval[] ad) {
        data=new byte[ad[0].data.length+ad[1].data.length];
        for (int i=0;i<ad[0].data.length;i+=2){
            data[i*2]=ad[0].data[i];
            data[i*2+1]=ad[0].data[i+1];
            data[i*2+2]=ad[1].data[i];
            data[i*2+3]=ad[1].data[i+1];
        }
    }

}

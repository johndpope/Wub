package com.klemstinegroup.wub2.system;

import com.echonest.api.v4.Segment;
import com.klemstinegroup.wub.ColorHelper;
import com.klemstinegroup.wub2.test.BeautifulKMGSR;
import com.klemstinegroup.wub2.test.ImagePanel;
import com.klemstinegroup.wub2.test.SongManager;
import com.klemstinegroup.wub2.test.Test3;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;

import com.klemstinegroup.wub.*;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.graphstream.graph.*;
import org.graphstream.graph.Node;

import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_ARGB;

public class Audio {

    private Java2DFrameConverter converter;
    private Robot robot;
    public static FFmpegFrameRecorder recorder;
    public transient SourceDataLine line;
    public transient Queue<AudioInterval> queue;

    transient int position = 0;
    transient AudioInterval currentlyPlaying;
    protected transient boolean breakPlay;
    public transient boolean pause = false;
    public transient boolean loop = false;

    public static final int resolution = 16;
    public static final int channels = 2;
    public static final int frameSize = channels * resolution / 8;
    public static final int sampleRate = 44100;
    public static final AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, resolution, channels, frameSize, sampleRate, false);
    public static final AudioFormat audioFormatMono = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, resolution, channels / 2, frameSize / 2, sampleRate, false);
    public static final int bufferSize = 8192;
    private Song cachedSong;
    private int cachedSongIndex;
    public static ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Node lastNode = null;
    private int start;
    private int lastSeg;

    public Audio() {
        this(null, null, 1);
    }

    public Audio(JFrame jframe, ImagePanel ip, int numClusters) {
        queue = new LinkedList<AudioInterval>();
        startPlaying(jframe, ip, numClusters);
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
        if (BeautifulKMGSR.makeVideo) {
            recorder = new FFmpegFrameRecorder(new File("out.mp4"), jframe.getWidth(), jframe.getHeight(), 2);
            recorder.setSampleRate((int) audioFormat.getSampleRate());
            recorder.setAudioChannels(2);
            recorder.setInterleaved(true);
            recorder.setVideoQuality(0);
            try {
                recorder.start();
            } catch (FrameRecorder.Exception e) {
                e.printStackTrace();
            }
            converter = new Java2DFrameConverter();
        }
    }

    ArrayList<Integer> tem = new ArrayList<>();


    public static HashMap<Integer, Color> randomColor = new HashMap<>();

    static {
        ArrayList<Integer> gill = new ArrayList<>();
        for (int i = 0; i < Test3.numClusters; i++) {
            gill.add(i);
        }

        for (int i = 0; i < Test3.numClusters; i++) {
            int pos = (int) (Math.random() * gill.size());
            double normd = ((double) pos / (double) Test3.numClusters) * 100d;
//            Test3.tf.setBackground(ColorHelper.numberToColor(normd));
            randomColor.put(i, ColorHelper.numberToColor(normd));
            gill.remove(new Integer(pos));
        }

    }

    private void startPlaying(JFrame jframe, ImagePanel tf, int numClusters) {
        HashMap<String, Integer> hm = new HashMap<>();
        line = getLine();
        new Thread(new Runnable() {
            public void run() {
                top:
                while (true) {
                    if (!queue.isEmpty()) {
                        AudioInterval i = queue.poll();
                        currentlyPlaying = i;
                        System.out.println("currently playing: " + i.payload);
                        if (i.payload != null) {
//                            System.out.println("now playing " + i.payload);

                            if (tf != null) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!tem.contains(i.payload.segment))
                                            tem.add(i.payload.segment);
                                        int norm = tem.indexOf(i.payload.segment);
//                                tf.append(norm + "\n");
                                        double normd = ((double) norm / (double) numClusters) * 100d;

//                                tf.invalidate();

                                        if (cachedSong == null || cachedSongIndex != i.payload.song) {
                                            cachedSong = SongManager.getRandom(i.payload.song);
                                            cachedSongIndex = i.payload.song;
                                        }

                                        if (i.payload != null && i.payload.segment > -1) {

                                            if (BeautifulKMGSR.makeVideo) {
                                                BufferedImage grab = robot.createScreenCapture(jframe.getBounds());
                                                Frame frame = converter.convert(grab);
                                                short[] samples = new short[i.data.length / 2];
                                                ByteBuffer.wrap(i.data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
                                                ShortBuffer sBuff = ShortBuffer.wrap(samples, 0, i.data.length / 2);
                                                frame.sampleRate = (int) audioFormat.getSampleRate();
                                                frame.audioChannels = 2;
                                                frame.samples = new Buffer[]{(Buffer) sBuff};
                                                frame.timestamp = start;
                                                start += 500 * (int) (1000 * (i.data.length / 2) / audioFormat.getSampleRate());
                                                try {
                                                    recorder.record(frame, AV_PIX_FMT_ARGB);
                                                    recorder.setTimestamp(start);
                                                } catch (FrameRecorder.Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                            ArrayList<Segment> list = new ArrayList<>();
                                            list.add(cachedSong.analysis.getSegments().get(i.payload.segment));
                                            double duration = cachedSong.analysis.getSegments().get(i.payload.segment).duration;
                                            tf.setBackground(ColorHelper.numberToColor(normd));
                                            BufferedImage bi = new SamplingGraph().createWaveForm(list, duration, i.data, audioFormat, tf.getWidth(), tf.getHeight());
                                            Graphics g = bi.getGraphics();

                                            g.setFont(new Font("Arial", Font.BOLD, 30));
                                            g.setColor(Color.RED);

                                            for (int xi = -1; xi < 2; xi++) {
                                                for (int yi = -1; yi < 2; yi++) {
                                                    g.drawString(i.payload.song + ":" + i.payload.segment, 60 - xi, 15 + yi + tf.getHeight() / 2);

                                                }

                                            }
                                            g.setColor(Color.BLACK);

                                            g.drawString(i.payload.song + ":" + i.payload.segment, 60, 15 + tf.getHeight() / 2);
                                            if (hm.get(lastSeg + "") == null)
                                                hm.put(lastSeg + "", 0);
                                            int val = hm.get(lastSeg + "") + 1;
                                            hm.put(lastSeg + "", val);
                                            lastSeg = i.payload.segment;
                                            Color color = ColorHelper.numberToColorPercentage((double) val / (double) BeautifulKMGSR.maxValue);
                                            if (lastNode != null) {
                                                lastNode.addAttribute("ui.style", "fill-color: rgb(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ");");
                                                lastNode.addAttribute("ui.style", "size: 15;");
                                            }
                                            Node node = BeautifulKMGSR.graph.getNode(i.payload.hashCode() + "");
                                            if (node != null) {
                                                node.addAttribute("ui.style", "fill-color: rgb(255,0,0);");
                                                node.addAttribute("ui.style", "size:25;");
                                            }

                                            lastNode = node;

                                            tf.setImage(bi);
                                            tf.invalidate();
                                        }
//                                System.out.println("hetre2");

                                    }
                                }).start();
                            }


                        }
                        int j = 0;
                        for (j = 0; j <= i.data.length - bufferSize; j += bufferSize) {
                            while (pause || breakPlay) {
                                if (breakPlay) {
                                    breakPlay = false;
                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    continue top;
                                }
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            position = j;
                            line.write(i.data, j, bufferSize);

                        }

                        if (j < i.data.length) {
                            position = j;
                            line.write(i.data, j, i.data.length - j);
                            // line.drain();
                        }
                        if (loop)
                            queue.add(i);
                    } else

                        currentlyPlaying = null;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).

                start();

    }

    public SourceDataLine getLine() {
        SourceDataLine res = null;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, Audio.audioFormat);
        try {
            res = (SourceDataLine) AudioSystem.getLine(info);
            res.open(Audio.audioFormat);
            res.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        return res;
    }

    public void play(AudioInterval i) {
        try {
            baos.write(i.data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        queue.add(i);
    }

}

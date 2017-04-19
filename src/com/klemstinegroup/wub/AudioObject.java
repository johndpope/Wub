package com.klemstinegroup.wub;

import com.echonest.api.v4.Segment;
import com.echonest.api.v4.TimedEvent;
import com.echonest.api.v4.TrackAnalysis;
import com.klemstinegroup.wub.system.*;
import com.sun.media.sound.WaveFileWriter;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.Queue;

public class AudioObject implements Serializable {

	/**
	 * 
	 
	 */
	private static final long serialVersionUID = 379377752113122689L;
	transient int filecount = 0;
	public byte[] data;
	public File file;
	public TrackAnalysis analysis;

	public transient MusicCanvas mc;
	public transient SourceDataLine line;
	public transient Queue<Interval> queue;
	public transient int position = 0;
	public transient Interval currentlyPlaying;
	public transient boolean breakPlay;
	public transient boolean pause = false;
	public transient boolean loop = false;
	public transient HashMap<String, Interval> midiMap;
	public static double tolerance = .1d;

	public static final int resolution = 16;
	public static final int channels = 2;
	public static final int frameSize = channels * resolution / 8;
	public static final int sampleRate = 44100;
	public static final AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, resolution, channels, frameSize, sampleRate, false);
	static final int bufferSize = 8192;
	public static String key = null;

	public AudioObject(String file) {
		this(new File(file),null);
	}

	public static AudioObject factory() {
		JFileChooser chooser = new JFileChooser(CentralCommand.lastDirectory);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("Audio", "mp3", "wav", "wub", "play");
		chooser.setFileFilter(filter);
		chooser.setSelectedFile(new File("spotify:track:6z0zyXMTA0ans4OoTAO2Bm"));
		int returnVal = chooser.showOpenDialog(new JFrame());
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			// System.out.println("You chose to open this file: " +
			CentralCommand.lastDirectory = chooser.getSelectedFile();
			if (chooser.getSelectedFile().getAbsolutePath().endsWith(".play")) {
				CentralCommand.loadPlay(chooser.getSelectedFile());
				return null;
			}
			return factory(chooser.getSelectedFile().getName(),null);
		}
		return null;
	}

	public static AudioObject factory(String file) {

		if (file.startsWith("spotify:track:")){
			try {
				return new MP3Grab().grab(file);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return factory(file,null);
	}

	public static AudioObject factory(String fileName, TrackAnalysis ta) {
		if (fileName.startsWith("spotify:track:")){
			try {
				return new MP3Grab().grab(fileName);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		File file=new File(fileName);
		File newFile = file;
		String extension = "";
		String filePrefix = "";
		int i = fileName.lastIndexOf('.');
		if (i > 0) {
			extension = fileName.substring(i + 1);
			filePrefix = fileName.substring(0, i);
		}
		if (extension.equals("play")) {
			CentralCommand.lastDirectory = file;
			CentralCommand.loadPlay(file);
			return null;
		}
		if (!extension.equals("wub")) {
			newFile = new File((file.getParent()==null?"":(file.getParent() + File.separator)) + filePrefix + ".wub");
			System.out.println(newFile.getAbsolutePath());
		}
		if (newFile.exists()) {
			try {
				AudioObject au = (AudioObject) Serializer.load(newFile);
				au.init(true);
				return au;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		AudioObject au = new AudioObject(file,ta);
		try {
			Serializer.store(au, newFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return au;
	}

	public AudioObject(final File file,TrackAnalysis ta) {

		this.file = file;
		convert(file);
		JTextArea msgLabel;
		JProgressBar progressBar;
		final int MAXIMUM = 100;
		JPanel panel;

		progressBar = new JProgressBar(0, MAXIMUM);
		progressBar.setIndeterminate(true);
		msgLabel = new JTextArea(file.getName());
		msgLabel.setEditable(false);

		panel = new JPanel(new BorderLayout(5, 5));
		panel.add(msgLabel, BorderLayout.PAGE_START);
		panel.add(progressBar, BorderLayout.CENTER);
		panel.setBorder(BorderFactory.createEmptyBorder(11, 11, 11, 11));

		final JDialog dialog = new JDialog();
		dialog.setTitle("Analyzing audio...");
		dialog.getContentPane().add(panel);
		dialog.setResizable(false);
		dialog.pack();
		dialog.setSize(500, dialog.getHeight());
		dialog.setLocationRelativeTo(null);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setAlwaysOnTop(false);
		dialog.setVisible(true);
		msgLabel.setBackground(panel.getBackground());
		if (ta!=null)analysis = ta;
		init(true);
		dialog.dispose();
	}

	public AudioObject(byte[] by, TrackAnalysis fa, File file) {
		this.file = file;
		analysis = fa;
		data = by;
		System.out.println(data.length);
		init(true);
	}

	public void init(boolean addtoCentral) {
		midiMap = new HashMap<String, Interval>();
		queue = new LinkedList<Interval>();
		mc = new MusicCanvas(this);
		if (addtoCentral)
			CentralCommand.add(this);
		startPlaying();
	}

	private void startPlaying() {
		line = getLine();
		new Thread(new Runnable() {
			public void run() {
				top: while (true) {

					// System.out.println(queue.size());
					if (!queue.isEmpty()) {
						Interval i = queue.poll();

						currentlyPlaying = i;
						int j = 0;
						for (j = Math.max(0,i.startBytes); j <= i.endBytes - bufferSize && j < data.length - bufferSize; j += bufferSize) {
							while (pause || breakPlay) {
								if (breakPlay) {
									breakPlay = false;
									// if (loop)
									// queue.add(i);
									// queue.clear();
									try {
										Thread.sleep(10);
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
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
							line.write(data, j, bufferSize);

						}

						if (j < i.endBytes && i.endBytes < data.length) {
							position = j;
							line.write(data, j, i.endBytes - j);
							// line.drain();
						}
						if (loop)
							queue.add(i);
					} else

						currentlyPlaying = null;
					if (!mc.mouseDown)
						mc.tempTimedEvent = null;
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	/*public TrackAnalysis echoNest(File file) {
		int cnt = 0;
		while (cnt < 5) {
			try {
				EchoNestAPI en = null;
				if (key != null)
					en = new EchoNestAPI(key);
				else
					en = new EchoNestAPI();
				Track track = en.uploadTrack(file);
				System.out.println(track);
				track.waitForAnalysis(30000);
				if (track.getStatus() == Track.AnalysisStatus.COMPLETE) {
					return track.getAnalysis();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				Thread.sleep(30000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return analysis;
	}*/

	public void convert(File soundFile) {
		AudioInputStream mp3InputStream = null;
		try {
			System.out.println(soundFile.getAbsolutePath());
			mp3InputStream = AudioSystem.getAudioInputStream(soundFile);
		} catch (UnsupportedAudioFileException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		File temp = new File("temp.wav");
		mp3InputStream = AudioSystem.getAudioInputStream(new AudioFormat(mp3InputStream.getFormat().getSampleRate(), resolution, AudioObject.channels, true, false), mp3InputStream);
		try {
			AudioSystem.write(mp3InputStream, AudioFileFormat.Type.WAVE, temp);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// try {
		// data = Files.readAllBytes(temp.toPath());
		try {
			mp3InputStream = AudioSystem.getAudioInputStream(temp);
		} catch (UnsupportedAudioFileException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		mp3InputStream = AudioSystem.getAudioInputStream(AudioObject.audioFormat, mp3InputStream);

		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		try {
			AudioSystem.write(mp3InputStream, AudioFileFormat.Type.WAVE, bo);
		} catch (IOException e) {
			e.printStackTrace();
		}
		data = bo.toByteArray();

		try {
			mp3InputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		temp.delete();
	}

	public String getFileName() {
		if (file==null)return null;
		return file.getName();
	}

	public SourceDataLine getLine() {
		SourceDataLine res = null;
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, AudioObject.audioFormat);
		try {
			res = (SourceDataLine) AudioSystem.getLine(info);
			res.open(AudioObject.audioFormat);
			res.start();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}
		return res;
	}

	public void play(Interval i) {
		queue.add(i);
	}

	// public void sendMidi(String keyName, int vel) {
	// System.out.println(keyName + "\t" + vel);
	// Interval i = midiMap.get(keyName);
	// if (vel > 0) {
	// if (i == null) {
	// if (mc.hovering != null)
	// midiMap.put(keyName, mc.hovering);
	// } else {
	// // if (queue.size()>0){
	// // breakPlay = true;
	// // }
	// // while (breakPlay) {
	// // try {
	// // Thread.sleep(10);
	// // } catch (InterruptedException e) {
	// // // TODO Auto-generated catch block
	// // e.printStackTrace();
	// // }
	// // }
	// // System.out.println("add");
	// queue.add(i);
	// }
	// }
	// // else {
	// // if (i != null) {
	// // if (i.equals(currentlyPlaying)) {
	// // breakPlay = true;
	// // }
	// // }
	// // }
	//
	// }

	// public void play(double start, double duration) {
	// int startInBytes = (int) (start * AudioObject.sampleRate *
	// AudioObject.frameSize) - (int) (start * AudioObject.sampleRate *
	// AudioObject.frameSize) % AudioObject.frameSize;
	// double lengthInFrames = duration * AudioObject.sampleRate;
	// int lengthInBytes = (int) (lengthInFrames * AudioObject.frameSize) -
	// (int) (lengthInFrames * AudioObject.frameSize) % AudioObject.frameSize;
	// queue.add(new Interval(startInBytes, Math.min(startInBytes +
	// lengthInBytes, data.length)));
	//
	// }


	public void reverse(byte[] array) {
		if (array == null) {
			return;
		}
		int i = 0;
		int j = array.length - 2;
		byte tmp1;
		byte tmp2;

		while (j > i) {
			tmp1 = array[j];
			tmp2 = array[j + 1];
			array[j] = array[i];
			array[j + 1] = array[i + 1];
			array[i] = tmp1;
			array[i + 1] = tmp2;
			j -= 2;
			i += 2;
		}
	}

	public void createAudioObject(TrackAnalysis ta) {
		boolean savePause = pause;
		pause = true;
		final FakeTrackAnalysis fa = new FakeTrackAnalysis();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		LinkedList<Interval> ll = new LinkedList<Interval>();
		if (currentlyPlaying != null) {
			ll.add(currentlyPlaying);
		}
		ll.addAll(queue);
		int bytecnt = 0;
		if (ll.size() == 0 && mc.hovering != null)
			ll.add(mc.hovering);
		if (ll.size() == 0)
			return;
		for (Interval i : ll) {
			i.newbytestart = bytecnt;
			baos.write(data, i.startBytes, i.lengthBytes);
			bytecnt += i.lengthBytes;

		}
		byte[] by = baos.toByteArray();
		fa.setDuration(convertByteToTime(by.length));
		Collections.sort(ll, new Comparator<Interval>() {

			@Override
			public int compare(Interval o1, Interval o2) {
				return Double.compare(o2.startBytes, o1.startBytes);
			}

		});
		for (Interval i : ll) {

			for (Segment e : analysis.getSegments()) {
				if (e.getStart() >= i.te.getStart() - 1d && e.getStart() + e.getDuration() <= i.te.getStart() + i.te.getDuration() + 1d) {
					Segment f = null;
					try {
						f = (Segment) Serializer.deepclone(e);
					} catch (ClassNotFoundException e1) {
						e1.printStackTrace();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					f.start = e.getStart() - i.te.getStart() + convertByteToTime(i.newbytestart);
					fa.segments.add(f);
				}
			}

			HashMap<String, Double> hm1 = new HashMap<String, Double>();
			hm1.put("start", 0d);
			hm1.put("duration", fa.getDuration());
			hm1.put("confidence", 1d);
			fa.sections.add(new TimedEvent(hm1));

			for (TimedEvent e : analysis.getBars()) {
				if (e.getStart() >= i.te.getStart() - tolerance && e.getStart() + e.getDuration() <= i.te.getStart() + i.te.getDuration() + tolerance) {
					HashMap<String, Double> hm = new HashMap<String, Double>();
					hm.put("start", e.getStart() - i.te.getStart() + convertByteToTime(i.newbytestart));
					hm.put("duration", e.getDuration());
					hm.put("confidence", e.getConfidence());
					fa.bars.add(new TimedEvent(hm));
				}
			}

			for (TimedEvent e : analysis.getBeats()) {
				if (e.getStart() >= i.te.getStart() - tolerance && e.getStart() + e.getDuration() <= i.te.getStart() + i.te.getDuration() + tolerance) {
					HashMap<String, Double> hm = new HashMap<String, Double>();
					hm.put("start", e.getStart() - i.te.getStart() + convertByteToTime(i.newbytestart));
					hm.put("duration", e.getDuration());
					hm.put("confidence", e.getConfidence());
					fa.beats.add(new TimedEvent(hm));
				}
			}

			for (TimedEvent e : analysis.getTatums()) {
				if (e.getStart() >= i.te.getStart() - tolerance && e.getStart() + e.getDuration() <= i.te.getStart() + i.te.getDuration() + tolerance) {
					HashMap<String, Double> hm = new HashMap<String, Double>();
					hm.put("start", e.getStart() - i.te.getStart() + convertByteToTime(i.newbytestart));
					hm.put("duration", e.getDuration());
					hm.put("confidence", e.getConfidence());
					fa.tatums.add(new TimedEvent(hm));
				}
			}

			Collections.sort(fa.segments, new Comparator<TimedEvent>() {
				@Override
				public int compare(TimedEvent o1, TimedEvent o2) {
					return Double.compare(o1.getStart(), o2.getStart());
				}

			});

			Collections.sort(fa.bars, new Comparator<TimedEvent>() {
				@Override
				public int compare(TimedEvent o1, TimedEvent o2) {
					return Double.compare(o1.getStart(), o2.getStart());
				}

			});
			Collections.sort(fa.beats, new Comparator<TimedEvent>() {
				@Override
				public int compare(TimedEvent o1, TimedEvent o2) {
					return Double.compare(o1.getStart(), o2.getStart());
				}

			});
			Collections.sort(fa.tatums, new Comparator<TimedEvent>() {
				@Override
				public int compare(TimedEvent o1, TimedEvent o2) {
					return Double.compare(o1.getStart(), o2.getStart());
				}

			});
		}

		System.out.println(fa.duration);

		String fileName = file.getAbsolutePath();
		String extension = "";
		String filePrefix = "";
		int i = fileName.lastIndexOf('.');
		if (i > 0) {
			extension = fileName.substring(i + 1);
			filePrefix = fileName.substring(0, i);
		}
		String filePrefix1 = null;
		do {
			filecount++;
			filePrefix1 = filePrefix + String.format("%03d", filecount);
		} while (new File(filePrefix1 + ".wav").exists());
		ByteArrayInputStream bais = new ByteArrayInputStream(by);
		long length = (long) (by.length / audioFormat.getFrameSize());
		AudioInputStream audioInputStreamTemp = new AudioInputStream(bais, audioFormat, length);
		WaveFileWriter writer = new WaveFileWriter();
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(filePrefix1 + ".wav");
			writer.write(audioInputStreamTemp, AudioFileFormat.Type.WAVE, fos);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		final File newFile = new File(filePrefix1 + ".wav");
		final File newFileWub = new File(filePrefix1 + ".wub");
		final AudioObject ao = new AudioObject(by, fa, newFile);
		new Thread(new Runnable() {
			@Override
			public void run() {
				TrackAnalysis analysis1 = ta;
				if (analysis1.getSegments().size() > 0) {
					fa.segments.clear();
					fa.segments.addAll(analysis1.getSegments());
				}
				if (analysis1.getSections().size() > 0) {
					fa.sections.clear();
					fa.sections.addAll(analysis1.getSections());
				}
				if (analysis1.getBars().size() > 0) {
					fa.bars.clear();
					fa.bars.addAll(analysis1.getBars());
				}
				if (analysis1.getBeats().size() > 0) {
					fa.beats.clear();
					fa.beats.addAll(analysis1.getBeats());
				}
				if (analysis1.getTatums().size() > 0) {
					fa.tatums.clear();
					fa.tatums.addAll(analysis1.getTatums());
				}
				ao.mc.paint1();
				try {
					Serializer.store(ao, newFileWub);

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();

		pause = savePause;

	}

	public double convertByteToTime(int pos) {
		return (double) pos / (double) AudioObject.sampleRate / (double) AudioObject.frameSize;
	}

	public int convertTimeToByte(double time) {
		int c = (int) (time * AudioObject.sampleRate * AudioObject.frameSize);
		c += c % AudioObject.frameSize;
		return c;
	}
}

package com.tema.rtsp;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.IplImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoController {
    
    private static final String RTSP_URL = "rtsp://localhost:8554/stream"; // Change to your RTSP URL
    private static final String OUTPUT_DIR = "recorded_videos";
    private static final int VIDEO_DURATION_SECONDS = 5;
    private static final int NUMBER_OF_VIDEOS = 3;
    private static final double FRAME_RATE = 30.0;
    
    private final Stage stage;
    private final ImageView imageView;
    private FFmpegFrameGrabber grabber;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isPlayingBack = new AtomicBoolean(false);
    private Thread streamThread;
    
    private final List<String> recordedVideos = Collections.synchronizedList(new ArrayList<>());
    
    public VideoController(Stage stage) {
        this.stage = stage;
        this.imageView = new ImageView();
        this.imageView.setPreserveRatio(true);
        this.imageView.fitWidthProperty().bind(stage.widthProperty());
        this.imageView.fitHeightProperty().bind(stage.heightProperty());
        
        // Create output directory if it doesn't exist
        createOutputDirectory();
    }
    
    public ImageView getImageView() {
        return imageView;
    }
    
    private void createOutputDirectory() {
        try {
            Path path = Paths.get(OUTPUT_DIR);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("Created output directory: " + OUTPUT_DIR);
            }
        } catch (Exception e) {
            System.err.println("Error creating output directory: " + e.getMessage());
        }
    }
    
    public void startRTSPStream() {
        streamThread = new Thread(() -> {
            try {
                System.out.println("Connecting to RTSP stream: " + RTSP_URL);
                grabber = new FFmpegFrameGrabber(RTSP_URL);
                grabber.setOption("rtsp_transport", "tcp");
                grabber.start();
                
                System.out.println("Connected to RTSP stream successfully!");
                System.out.println("Stream dimensions: " + grabber.getImageWidth() + "x" + grabber.getImageHeight());
                
                OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
                Java2DFrameConverter paintConverter = new Java2DFrameConverter();
                
                while (isRunning.get() && !isPlayingBack.get()) {
                    Frame frame = grabber.grab();
                    if (frame != null && frame.image != null) {
                        BufferedImage bufferedImage = paintConverter.convert(frame);
                        if (bufferedImage != null) {
                            WritableImage image = SwingFXUtils.toFXImage(bufferedImage, null);
                            Platform.runLater(() -> imageView.setImage(image));
                        }
                    }
                    
                    // Small delay to prevent overwhelming the UI thread
                    Thread.sleep(33); // ~30 FPS
                }
                
            } catch (Exception e) {
                System.err.println("Error in RTSP stream: " + e.getMessage());
                e.printStackTrace();
            }
        });
        streamThread.setDaemon(true);
        streamThread.start();
    }
    
    public void startRecording() {
        if (isRecording.get()) {
            System.out.println("Recording already in progress!");
            return;
        }
        
        if (isPlayingBack.get()) {
            System.out.println("Cannot record while playing back!");
            return;
        }
        
        Thread recordingThread = new Thread(() -> {
            isRecording.set(true);
            System.out.println("Starting to record " + NUMBER_OF_VIDEOS + " videos...");
            
            for (int i = 0; i < NUMBER_OF_VIDEOS; i++) {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String filename = OUTPUT_DIR + "/video_" + timestamp + "_" + (i + 1) + ".mp4";
                
                System.out.println("Recording video " + (i + 1) + "/" + NUMBER_OF_VIDEOS + ": " + filename);
                
                if (recordVideo(filename, VIDEO_DURATION_SECONDS)) {
                    recordedVideos.add(filename);
                    System.out.println("Completed video " + (i + 1) + "/" + NUMBER_OF_VIDEOS);
                } else {
                    System.err.println("Failed to record video " + (i + 1));
                }
                
                // Small delay between recordings
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            isRecording.set(false);
            System.out.println("Recording completed! Total videos: " + recordedVideos.size());
        });
        recordingThread.setDaemon(true);
        recordingThread.start();
    }
    
    private boolean recordVideo(String outputFile, int durationSeconds) {
        FFmpegFrameRecorder recorder = null;
        FFmpegFrameGrabber recordGrabber = null;
        
        try {
            // Create a new grabber for recording
            recordGrabber = new FFmpegFrameGrabber(RTSP_URL);
            recordGrabber.setOption("rtsp_transport", "tcp");
            recordGrabber.start();
            
            // Create recorder
            recorder = new FFmpegFrameRecorder(outputFile, recordGrabber.getImageWidth(), recordGrabber.getImageHeight());
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(FRAME_RATE);
            recorder.setVideoBitrate(2000000);
            recorder.start();
            
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000);
            
            while (System.currentTimeMillis() < endTime) {
                Frame frame = recordGrabber.grab();
                if (frame != null) {
                    recorder.record(frame);
                }
            }
            
            recorder.stop();
            recorder.release();
            recordGrabber.stop();
            recordGrabber.release();
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error recording video: " + e.getMessage());
            e.printStackTrace();
            
            try {
                if (recorder != null) {
                    recorder.stop();
                    recorder.release();
                }
                if (recordGrabber != null) {
                    recordGrabber.stop();
                    recordGrabber.release();
                }
            } catch (Exception ex) {
                // Ignore cleanup errors
            }
            
            return false;
        }
    }
    
    public void startPlayback() {
        if (isPlayingBack.get()) {
            System.out.println("Playback already in progress!");
            return;
        }
        
        if (isRecording.get()) {
            System.out.println("Cannot playback while recording!");
            return;
        }
        
        if (recordedVideos.isEmpty()) {
            System.out.println("No recorded videos available for playback!");
            return;
        }
        
        Thread playbackThread = new Thread(() -> {
            isPlayingBack.set(true);
            
            // Create a copy and reverse it for playback
            List<String> videosToPlay = new ArrayList<>(recordedVideos);
            Collections.reverse(videosToPlay);
            
            System.out.println("Starting playback of " + videosToPlay.size() + " videos in reverse chronological order...");
            
            for (int i = 0; i < videosToPlay.size(); i++) {
                String videoFile = videosToPlay.get(i);
                System.out.println("Playing video " + (i + 1) + "/" + videosToPlay.size() + ": " + videoFile);
                playVideo(videoFile);
            }
            
            isPlayingBack.set(false);
            System.out.println("Playback completed! Returning to live stream...");
            
            // Restart the live stream
            if (isRunning.get()) {
                startRTSPStream();
            }
        });
        playbackThread.setDaemon(true);
        playbackThread.start();
    }
    
    private void playVideo(String videoFile) {
        FFmpegFrameGrabber playbackGrabber = null;
        
        try {
            File file = new File(videoFile);
            if (!file.exists()) {
                System.err.println("Video file not found: " + videoFile);
                return;
            }
            
            playbackGrabber = new FFmpegFrameGrabber(file);
            playbackGrabber.start();
            
            Java2DFrameConverter converter = new Java2DFrameConverter();
            
            double frameRate = playbackGrabber.getFrameRate();
            long frameDelay = (long) (1000.0 / frameRate);
            
            Frame frame;
            while ((frame = playbackGrabber.grab()) != null && isPlayingBack.get()) {
                if (frame.image != null) {
                    BufferedImage bufferedImage = converter.convert(frame);
                    if (bufferedImage != null) {
                        WritableImage image = SwingFXUtils.toFXImage(bufferedImage, null);
                        Platform.runLater(() -> imageView.setImage(image));
                    }
                }
                
                Thread.sleep(frameDelay);
            }
            
            playbackGrabber.stop();
            playbackGrabber.release();
            
        } catch (Exception e) {
            System.err.println("Error playing video: " + e.getMessage());
            e.printStackTrace();
            
            try {
                if (playbackGrabber != null) {
                    playbackGrabber.stop();
                    playbackGrabber.release();
                }
            } catch (Exception ex) {
                // Ignore cleanup errors
            }
        }
    }
    
    public void cleanup() {
        System.out.println("Cleaning up resources...");
        isRunning.set(false);
        isRecording.set(false);
        isPlayingBack.set(false);
        
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
            }
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
        
        if (streamThread != null && streamThread.isAlive()) {
            streamThread.interrupt();
        }
    }
}

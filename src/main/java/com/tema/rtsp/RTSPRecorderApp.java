package com.tema.rtsp;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public class RTSPRecorderApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create the video player controller
        VideoController videoController = new VideoController(primaryStage);
        
        // Create the root pane
        StackPane root = new StackPane();
        root.getChildren().add(videoController.getImageView());
        
        // Create scene with transparent background
        Scene scene = new Scene(root, 800, 600);
        scene.setFill(Color.TRANSPARENT);
        
        // Setup keyboard handlers
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case DIGIT1:
                case NUMPAD1:
                    System.out.println("Key 1 pressed - Starting recording...");
                    videoController.startRecording();
                    break;
                case DIGIT2:
                case NUMPAD2:
                    System.out.println("Key 2 pressed - Starting playback...");
                    videoController.startPlayback();
                    break;
                case ESCAPE:
                    System.out.println("ESC pressed - Exiting...");
                    videoController.cleanup();
                    primaryStage.close();
                    System.exit(0);
                    break;
                default:
                    break;
            }
        });
        
        // Configure stage - transparent and undecorated
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RTSP Recorder");
        primaryStage.setOnCloseRequest(event -> {
            videoController.cleanup();
            System.exit(0);
        });
        
        primaryStage.show();
        
        // Start the RTSP stream
        videoController.startRTSPStream();
        
        System.out.println("Application started!");
        System.out.println("Press 1 to record 3 videos of 5 seconds each");
        System.out.println("Press 2 to playback recorded videos in reverse chronological order");
        System.out.println("Press ESC to exit");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

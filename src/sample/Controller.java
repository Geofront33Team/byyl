package sample;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.SplittableRandom;

public class Controller {
    @FXML private Text actiontarget;
    @FXML public TextArea inputArea;
    @FXML public TextArea outputArea;
    @FXML public TextField fileNameArea;

    Scanner readFile(String path){
        File f = new File(path);
        Scanner in = null;
        try {
            in = new Scanner(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return in;
    }

    public Controller() throws FileNotFoundException {
    }

    @FXML protected void func(ActionEvent event) throws IOException {
        File f=new File("input.txt");
        FileOutputStream fos1=new FileOutputStream(f);
        OutputStreamWriter dos1=new OutputStreamWriter(fos1);
        dos1.write(inputArea.getText());
        dos1.close();
        outputArea.setText(inputArea.getText());
    }
    @FXML protected void handleUploadFileButtonAction(ActionEvent event) throws IOException {
        System.out.println("浏览文件");
        Stage stage = null;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File selectedFile = fileChooser.showOpenDialog(stage);
        System.out.println(selectedFile);
        fileNameArea.setText(selectedFile.toString());
        FileInputStream fileInputStream = new FileInputStream(selectedFile);
        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        StringBuffer sb = new StringBuffer();
        String text;
        while((text = bufferedReader.readLine()) != null){
            sb.append(text);
            sb.append('\n');
        }
        inputArea.setText(sb.toString());
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gasstation;

import com.sun.javacard.apduio.*;
import static com.sun.javacard.apduio.Apdu.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Ariya
 */
public class GasStation extends javax.swing.JFrame {

    // copy form card application
    final static byte SSGS_CLA = (byte) 0x80;
    final static byte VERIFY = (byte) 0x01;
    final static byte GET_BALANCE = (byte) 0x02;
    final static byte UPDATE_PURCHASE_INFO = (byte) 0x03;
    final static byte GET_PURCHASE_HISTORIES = (byte) 0x04;
    final static byte GET_PURCHASE_HISTORIES_BY_TIME = (byte) 0x05;
    final static byte GET_PURCHASE_HISTORIES_BY_STATION = (byte) 0x06;
    final static byte MAX_PIN_SIZE = (byte) 0x08;
    final static short SW_VERIFICATION_FAILED = 0x6300;
    final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;
    final static short SW_NOT_ENOUGH_ACCOUNT_BALANCE = 0x6302;
    final static short INVALID_UPDATE_PURCHASE_INFO = 0x6303;
    final static short INVALID_STATION_SIGNATURE = 0x6304;
    final static short TLV_EXCEPTION = 0x6305;
    final static short ARITHMETIC_EXCEPTION = 0x6306;
    final static short INVAILD_NUMBER_FORMAT = 0x6307;
    final static short SW_PURCHASE_INFO_NOT_FOUND = 0x6308;
    final static short SW_PIN_IS_BLOCKED = 0x6309;

    // define constant
    final static int GASOLINE_PRICE = (int) 23500;
    final static String STATION_ID = "AA123";

    // max data length
    final static int MAX_DATA_LENGTH = (int) 1024;

    // define the code of screen
    final static int SCR_INITIAL = (int) 10;
    final static int SCR_VALIDATE = (int) 20;
    final static int SCR_VALIDATE_FAILED = (int) 21;
    final static int SCR_PIN_IS_BLOCKED = (int) 22;
    final static int SCR_MAIN = (int) 30;
    final static int SCR_REFUEL = (int) 40;
    final static int SCR_REFUEL_COMPLETE = (int) 41;

    final static int SCR_GET_BALANCE = (int) 50;
    final static int SCR_GET_HISTORIES = (int) 60;
    final static int SCR_GET_HISTORIES_BY_TIME = (int) 70;
    final static int SCR_GET_HISTORIES_BY_STATION = (int) 80;
    final static int SCR_GET_HISTORIES_RESULT = (int) 61;

    final static int SCR_CHANGE_PIN = (int) 90;
    
    final static int SCR_ERR = (int) 100;

    // for connect the card
    CadClientInterface cad;
    Socket sock;
    InputStream is;
    OutputStream os;

    // array for input data
    String inputString;
    char inputArr[];
    int Lc;

    // array for input data, output apdu command
    byte dataIn[];
    byte dataOut[];
    
    // array for output data in textarea
    String outputString;
    
    // string to announce err
    String errString;

    // declare variable
    int maxAmount;
    int amountOfGasoline;
    int screen;
    int backToScreen;

    // variable for 
    int balance;

    // function set label for select button
    private void setLabel(String str1, String str2, String str3, String str4, String str5) {
        lblForBtn1.setText(str1);
        lblForBtn2.setText(str2);
        lblForBtn3.setText(str3);
        lblForBtn4.setText(str4);
        lblForBtn5.setText(str5);
    }

    private void setScreen(int screenID) {
        screen = screenID;
        switch (screenID) {
            case SCR_VALIDATE:
                announce.setText("Please enter your PIN:");
                setLabel(null, null, null, null, null);
                break;
            case SCR_VALIDATE_FAILED:
                announce.setText("Invalid PIN!");
                setLabel("BACK", null, null, null, null);
                break;
            case SCR_PIN_IS_BLOCKED:
                announce.setText("PIN is blocked!");
                setLabel("EXIT", null, null, null, null);
                break;
            case SCR_MAIN:
                announce.setText("Please select your choice:");
                setLabel("REFUEL", "GET BALANCE", "GET HISTORIES", "CHANGE PIN", "EXIT");
                break;
            case SCR_REFUEL:
                announce.setText("Refueling...");
                setLabel("OK", null, null, null, null);
                break;
            case SCR_REFUEL_COMPLETE:
                announce.setText("Refueled!");
                setLabel("EXPORT BILL", "BACK", null, null, null);
                break;
            case SCR_GET_BALANCE:
                announce.setText("Your account balance is:" + balance);
                setLabel("BACK", null, null, null, null);
                break;
            case SCR_GET_HISTORIES:
                announce.setText("Please select your choice");
                setLabel("BY TIME", "BY STATION", "ALL", "RECENT", null);
                break;
            case SCR_GET_HISTORIES_BY_TIME:
                announce.setText("Please enter the time:");
                setLabel(null, null, null, null, null);
                break;
            case SCR_GET_HISTORIES_BY_STATION:
                announce.setText("Please enter the station ID");
                setLabel(null, null, null, null, null);
                break;
            case SCR_GET_HISTORIES_RESULT:
                announce.setText(outputString);
                setLabel("EXPORT BILL", "BACK", null, null, null);
                break;
            case SCR_CHANGE_PIN:
                announce.setText("Day la man hinh change PIN");
                setLabel(null, null, null, null, null);
                break;
            case SCR_INITIAL:
                announce.setText("Man hinh ban dau");
                setLabel(null, null, null, null, null);
                break;
            case SCR_ERR:
                announce.setText(errString);
                setLabel("BACK", null, null, null, null);
            default:
        }
    }
    
    /**
     * convenient function
     */
    private byte[] createCharArr(String str) {
        byte result[] = new byte[str.length()];
        char charArr[] = str.toCharArray();
        for(int i = 0; i < str.length(); i++) {
            result[i] = (byte) charArr[i];
        }
        return result;
    }
    
    /**
     * convenient function
     */
    private String createDateTime(String time) {
        char timeArr[] = time.toCharArray();
        return "20"+timeArr[0]+timeArr[1]+"/"+timeArr[2]+timeArr[3]+"/"+timeArr[4]+timeArr[5]+" "+timeArr[6]+timeArr[7]+":"+timeArr[8]+timeArr[9];
    }
    
    /**
     * get string form bytes array
     */
    private String getStringFromByteArray(byte[] bArray, int bOffset, int bLength) {
        char result[] = new char[bLength];
        for (int i = 0; i < bLength; i++) {
            result[i] = (char) bArray[bOffset + i];
        }
        return new String(result);
    }
    
    /**
     * create output for get histories function
     */
    private String getOutputHistories(byte[] bArray) {
        String result = "";
        String history;
        ByteBuffer bb = ByteBuffer.wrap(bArray);
        int offset;
        int len = bArray.length;
        if (len <= 134) {
            offset = 2;
        } else {
            offset = 4;
        }
        while (offset < len) {
            history = "Station ID: " + getStringFromByteArray(bArray, offset + 4, 5) + "\r\n";
            history += "Time: " + createDateTime(getStringFromByteArray(bArray, offset + 11, 10)) + "\r\n";
            history += "Amount: " + bb.getInt(offset + 23) + "\r\n";
            history += "Price: " + bb.getInt(offset + 29) + "\r\n";
            result += "\r\n" + history;
            offset += 33;
        }
        return result;
    }

    /**
     * Creates new form GasStation
     */
    public GasStation() throws IOException, CadTransportException {
        initComponents();

        // initialize the dataIn, dataOut
        dataIn = new byte[MAX_DATA_LENGTH];

        // test
        setScreen(SCR_VALIDATE);
        price.setText(Integer.toString(GASOLINE_PRICE));
        price.setEditable(false);
        stationID.setText(STATION_ID);
        stationID.setEditable(false);
        announce.setEditable(false);
        // insert code to connect the card, init maxAmount, screen (sau nay them code chon card, copy sang cho khac

        // code for connect the card
        sock = new Socket("localhost", 9025);
        is = sock.getInputStream();
        os = sock.getOutputStream();
        cad = CadDevice.getCadClientInstance(CadDevice.PROTOCOL_T1, is, os);
        cad.powerUp();
        Apdu apdu = new Apdu();
        apdu.command[CLA] = (byte) 0x00;
        apdu.command[INS] = (byte) 0xa4;
        apdu.command[P1] = (byte) 0x04;
        apdu.command[P2] = (byte) 0x00;
        byte[] dataIn = {(byte) 0x92, (byte) 0x25, (byte) 0xb1, (byte) 0xd8, (byte) 0xaa, (byte) 0x74};
        apdu.setDataIn(dataIn, 6);
        cad.exchangeApdu(apdu);
        System.out.println(apdu);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        stationID = new javax.swing.JTextField();
        amount = new javax.swing.JTextField();
        currentTime = new javax.swing.JTextField();
        price = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        lblForBtn1 = new javax.swing.JLabel();
        btnSelection1 = new javax.swing.JButton();
        lblForBtn2 = new javax.swing.JLabel();
        btnSelection2 = new javax.swing.JButton();
        lblForBtn3 = new javax.swing.JLabel();
        btnSelection3 = new javax.swing.JButton();
        lblForBtn4 = new javax.swing.JLabel();
        btnSelection4 = new javax.swing.JButton();
        lblForBtn5 = new javax.swing.JLabel();
        btnSelection5 = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        announce = new javax.swing.JTextArea();
        jPanel5 = new javax.swing.JPanel();
        inputText = new javax.swing.JTextField();
        btnSubmit = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(204, 204, 255));

        stationID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stationIDActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(stationID)
                    .addComponent(amount, javax.swing.GroupLayout.DEFAULT_SIZE, 118, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(currentTime, javax.swing.GroupLayout.DEFAULT_SIZE, 108, Short.MAX_VALUE)
                    .addComponent(price)))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(stationID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(currentTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(amount, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(price, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        jPanel2.setBackground(new java.awt.Color(204, 204, 255));

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 114, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jPanel3.setBackground(new java.awt.Color(204, 204, 255));

        btnSelection1.setText("1");
        btnSelection1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSelection1ActionPerformed(evt);
            }
        });

        btnSelection2.setText("2");
        btnSelection2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSelection2ActionPerformed(evt);
            }
        });

        btnSelection3.setText("3");
        btnSelection3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSelection3ActionPerformed(evt);
            }
        });

        btnSelection4.setText("4");
        btnSelection4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSelection4ActionPerformed(evt);
            }
        });

        btnSelection5.setText("5");
        btnSelection5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSelection5ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(lblForBtn4, javax.swing.GroupLayout.DEFAULT_SIZE, 88, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnSelection4))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblForBtn3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblForBtn2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblForBtn1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(btnSelection1)
                        .addComponent(btnSelection2, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(btnSelection3, javax.swing.GroupLayout.Alignment.TRAILING)))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addComponent(lblForBtn5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnSelection5))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnSelection1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblForBtn1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnSelection2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblForBtn2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnSelection3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblForBtn3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnSelection4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblForBtn4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnSelection5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblForBtn5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );

        jPanel4.setBackground(new java.awt.Color(204, 204, 255));

        announce.setColumns(20);
        announce.setRows(5);
        jScrollPane1.setViewportView(announce);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 232, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE)
        );

        jPanel5.setBackground(new java.awt.Color(204, 204, 255));

        btnSubmit.setText("Enter");
        btnSubmit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSubmitActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addComponent(inputText, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnSubmit, javax.swing.GroupLayout.DEFAULT_SIZE, 98, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(inputText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSubmit))
                .addGap(0, 3, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void stationIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stationIDActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_stationIDActionPerformed

    private void btnSelection1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelection1ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_VALIDATE_FAILED:
                setScreen(SCR_VALIDATE);
                break;
            case SCR_PIN_IS_BLOCKED:
                setScreen(SCR_INITIAL);
                break;
            case SCR_MAIN:
                setScreen(SCR_REFUEL);
                break;
            case SCR_REFUEL:
                setScreen(SCR_REFUEL_COMPLETE);
                break;
            case SCR_GET_BALANCE:
                setScreen(SCR_MAIN);
                break;
            case SCR_GET_HISTORIES:
                setScreen(SCR_GET_HISTORIES_BY_TIME);
                break;
            case SCR_ERR:
                setScreen(backToScreen);
                break;
        }
    }//GEN-LAST:event_btnSelection1ActionPerformed

    private void btnSubmitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSubmitActionPerformed
        // TODO add your handling code here:
        Apdu apdu = new Apdu();
        switch (screen) {
            case SCR_VALIDATE:
                // 01 code xac thuc pin o day, co 3 truong hop: thanh cong, that bai, PIN bi block
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = VERIFY;
                inputString = inputText.getText();
                inputArr = inputString.toCharArray();
                Lc = inputArr.length;
                for (int i = 0; i < Lc; i++) {
                    dataIn[i] = (byte) (inputArr[i] - '0');
                }
                if (Lc == 0) {
                    Lc = 1; //need edit, tranh loi 6f00
                }
                apdu.setDataIn(dataIn, Lc);
                try {
                    cad.exchangeApdu(apdu);
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                } catch (CadTransportException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println(apdu);
                if (apdu.getStatus() == 0x9000) {
                    setScreen(SCR_MAIN);
                }
                if (apdu.getStatus() == (int) SW_VERIFICATION_FAILED) {
                    setScreen(SCR_VALIDATE_FAILED);
                }
                if (apdu.getStatus() == (int) SW_PIN_IS_BLOCKED) {
                    setScreen(SCR_PIN_IS_BLOCKED);
                }
                break;
            case SCR_GET_HISTORIES_BY_TIME:
                // if input is valid
                if (inputText.getText().length() != 6) {
                    errString = "Invalid format!";
                    backToScreen = SCR_GET_HISTORIES_BY_TIME;
                    setScreen(SCR_ERR);
                    break;
                }
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = GET_PURCHASE_HISTORIES_BY_TIME;
                dataIn = createCharArr(inputText.getText());
                apdu.setDataIn(dataIn);
                try {
                    cad.exchangeApdu(apdu);
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                } catch (CadTransportException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println(apdu);
                if (apdu.getStatus() == 0x9000) {
                    outputString = getOutputHistories(apdu.dataOut);
                    setScreen(SCR_GET_HISTORIES_RESULT);
                }
                if (apdu.getStatus() == 0x6308) {
                    errString = "No result!";
                    backToScreen = SCR_MAIN;
                    setScreen(SCR_ERR);
                }
                break;
            case SCR_GET_HISTORIES_BY_STATION:
                // if input is valid
                if (inputText.getText().length() != 5) {
                    errString = "Invalid format!";
                    backToScreen = SCR_GET_HISTORIES_BY_STATION;
                    setScreen(SCR_ERR);
                    break;
                }
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = GET_PURCHASE_HISTORIES_BY_STATION;
                dataIn = createCharArr(inputText.getText());
                apdu.setDataIn(dataIn);
                try {
                    cad.exchangeApdu(apdu);
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                } catch (CadTransportException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println(apdu);
                if (apdu.getStatus() == 0x9000) {
                    outputString = getOutputHistories(apdu.dataOut);
                    setScreen(SCR_GET_HISTORIES_RESULT);
                }
                if (apdu.getStatus() == 0x6308) {
                    errString = "No result!";
                    backToScreen = SCR_MAIN;
                    setScreen(SCR_ERR);
                }
                break;
        }
    }//GEN-LAST:event_btnSubmitActionPerformed

    private void btnSelection2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelection2ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_MAIN:
                // get balance fuction
                
                // send get balance apdu
                Apdu apdu = new Apdu();
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = GET_BALANCE;
                apdu.setLc(0);
                try {
                    cad.exchangeApdu(apdu);
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                } catch (CadTransportException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println(apdu);
                byte balanceArr[] = apdu.getDataOut();
                balance = ByteBuffer.wrap(balanceArr, 0, 4).getInt();
                setScreen(SCR_GET_BALANCE);
                break;
            case SCR_REFUEL_COMPLETE:
                setScreen(SCR_MAIN);
                break;
            case SCR_GET_HISTORIES:
                setScreen(SCR_GET_HISTORIES_BY_STATION);
                break;
            case SCR_GET_HISTORIES_RESULT:
                setScreen(SCR_MAIN);
                break;
        }
    }//GEN-LAST:event_btnSelection2ActionPerformed

    private void btnSelection3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelection3ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_MAIN:
                setScreen(SCR_GET_HISTORIES);
                break;
            case SCR_GET_HISTORIES:
                Apdu apdu = new Apdu();
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = GET_PURCHASE_HISTORIES;
                apdu.setLc(0);
                try {
                    cad.exchangeApdu(apdu);
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                } catch (CadTransportException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println(apdu);
                System.out.println(apdu.dataOut.length);
                outputString = getOutputHistories(apdu.dataOut);
                setScreen(SCR_GET_HISTORIES_RESULT);
                break;
        }
    }//GEN-LAST:event_btnSelection3ActionPerformed

    private void btnSelection4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelection4ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_MAIN:
                setScreen(SCR_CHANGE_PIN);
                break;
        }
    }//GEN-LAST:event_btnSelection4ActionPerformed

    private void btnSelection5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelection5ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_MAIN:
                setScreen(SCR_INITIAL);
                break;
        }
    }//GEN-LAST:event_btnSelection5ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(GasStation.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(GasStation.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(GasStation.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(GasStation.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    new GasStation().setVisible(true);
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                } catch (CadTransportException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField amount;
    private javax.swing.JTextArea announce;
    private javax.swing.JButton btnSelection1;
    private javax.swing.JButton btnSelection2;
    private javax.swing.JButton btnSelection3;
    private javax.swing.JButton btnSelection4;
    private javax.swing.JButton btnSelection5;
    private javax.swing.JButton btnSubmit;
    private javax.swing.JTextField currentTime;
    private javax.swing.JTextField inputText;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblForBtn1;
    private javax.swing.JLabel lblForBtn2;
    private javax.swing.JLabel lblForBtn3;
    private javax.swing.JLabel lblForBtn4;
    private javax.swing.JLabel lblForBtn5;
    private javax.swing.JTextField price;
    private javax.swing.JTextField stationID;
    // End of variables declaration//GEN-END:variables
}

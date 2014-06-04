/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gasstation;

import com.sun.javacard.apduio.*;
import static com.sun.javacard.apduio.Apdu.*;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.Timer;

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
    final static byte GET_LAST_PURCHASE_HISTORY = (byte) 0x07;
    final static byte CHANGE_PIN = (byte) 0x08;
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
    private static final byte[] dummySignature = {(byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88};

    // define constant
    final static int GASOLINE_PRICE = (int) 200;
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
    final static int SCR_REFUEL_CONFIRM = (int) 42;
    final static int SCR_REFUEL_COMPLETE = (int) 41;

    final static int SCR_GET_BALANCE = (int) 50;
    final static int SCR_GET_HISTORIES = (int) 60;
    final static int SCR_GET_HISTORIES_BY_TIME = (int) 70;
    final static int SCR_GET_HISTORIES_BY_STATION = (int) 80;
    final static int SCR_GET_HISTORIES_RESULT = (int) 61;

    final static int SCR_CHANGE_PIN = (int) 90;
    final static int SCR_CONFIRM_PIN = (int) 91;

    final static int SCR_NAVIGATION = (int) 100;

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
    String noticeString;

    // export bill file name
    final static String billFile = "Bill.txt";

    // declare variable
    int maxAmount;
    int amountOfGasoline;
    int screen;
    int backToScreen;

    // variable for 
    int balance;

    // variable for change PIN
    String newPIN;
    String confirmPIN;

    // variable for start cref
    JFileChooser fc;
    Process cref;

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
                setLabel("EXIT", null, null, null, null);
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
                announce.setText("Amount of Gasoline:");
                setLabel(null, null, null, null, null);
                break;
            case SCR_REFUEL_CONFIRM:
                announce.setText("Please enter OK button to confirm the purchase");
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
                announce.setText("Please enter the new PIN");
                setLabel(null, null, null, null, null);
                break;
            case SCR_CONFIRM_PIN:
                announce.setText("Confirm:");
                setLabel(null, null, null, null, null);
                break;
            case SCR_INITIAL:
                // reset all value
                // cref
                try {
                    Runtime.getRuntime().exec("taskkill /F /IM cref_tdual.exe");
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
                // amount variable
                maxAmount = 0;
                amountOfGasoline = 0;
                // enable select card button
                selectCard.setEnabled(true);

                announce.setText("Please insert the card");
                setLabel(null, null, null, null, null);
                break;
            case SCR_NAVIGATION:
                announce.setText(noticeString);
                setLabel("BACK", null, null, null, null);
            default:
        }
    }

    /**
     * convenient function
     */
    public String createStringFromTime(int number) {
        if (number < 10) {
            return "0" + number;
        }
        return Integer.toString(number);
    }

    /**
     * convenient function
     */
    public byte[] createUpdateInfo() throws IOException {
        // get current time
        Calendar calendar = Calendar.getInstance();
        int years = calendar.get(Calendar.YEAR);
        int months = calendar.get(Calendar.MONTH) + 1; // because of day counter from 0
        int days = calendar.get(Calendar.DAY_OF_MONTH);
        int hours = calendar.get(Calendar.HOUR_OF_DAY);
        int minutes = calendar.get(Calendar.MINUTE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // write update purchase info tlv header
        baos.write(new byte[]{(byte) 0xe3, (byte) 0x29});
        // write id tlv
        baos.write(new byte[]{(byte) 0xc4, (byte) 0x05});
        baos.write(createByteArr(STATION_ID));
        // write byteTime tlv
        baos.write(new byte[]{(byte) 0xc5, (byte) 0x0a});
        baos.write(createByteArr(createStringFromTime(years - 2000)));
        baos.write(createByteArr(createStringFromTime(months)));
        baos.write(createByteArr(createStringFromTime(days)));
        baos.write(createByteArr(createStringFromTime(hours)));
        baos.write(createByteArr(createStringFromTime(minutes)));
        // write amount tlv
        baos.write(new byte[]{(byte) 0xc6, (byte) 0x04});
        baos.write(ByteBuffer.allocate(4).putInt(amountOfGasoline).array());
        // write price tlv
        baos.write(new byte[]{(byte) 0xc7, (byte) 0x04});
        baos.write(ByteBuffer.allocate(4).putInt(GASOLINE_PRICE).array());
        // write sign tlv
        baos.write(new byte[]{(byte) 0xc8, (byte) 0x08});
        baos.write(dummySignature);

        // return the result
        return baos.toByteArray();

    }

    /**
     * convenient function
     */
    public String currentTime() {
        Calendar calendar = Calendar.getInstance();
        int years = calendar.get(Calendar.YEAR);
        int months = calendar.get(Calendar.MONTH) + 1; //because of mouth counter from 0
        int days = calendar.get(Calendar.DAY_OF_MONTH);
        int hours = calendar.get(Calendar.HOUR_OF_DAY);
        int minutes = calendar.get(Calendar.MINUTE);
        int seconds = calendar.get(Calendar.SECOND);
        String currentTime = years + "/" + months + "/" + days + " " + hours + ":" + minutes + ":" + seconds;
        return currentTime;
    }

    /**
     * convenient function
     */
    private byte[] createByteArr(String str) {
        byte result[] = new byte[str.length()];
        char charArr[] = str.toCharArray();
        for (int i = 0; i < str.length(); i++) {
            result[i] = (byte) charArr[i];
        }
        return result;
    }

    /**
     * convenient function
     */
    private String createDateTime(String time) {
        char timeArr[] = time.toCharArray();
        return "20" + timeArr[0] + timeArr[1] + "/" + timeArr[2] + timeArr[3] + "/" + timeArr[4] + timeArr[5] + " " + timeArr[6] + timeArr[7] + ":" + timeArr[8] + timeArr[9];
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
        fc = new JFileChooser("C:\\Users\\Ariya\\Desktop");

        // initialize the dataIn, dataOut
        dataIn = new byte[MAX_DATA_LENGTH];

        // test
        price.setBorder(BorderFactory.createLineBorder(Color.white));
        currentTime.setBorder(BorderFactory.createLineBorder(Color.white));
        stationID.setBorder(BorderFactory.createLineBorder(Color.white));
        amount.setBorder(BorderFactory.createLineBorder(Color.white));

        setScreen(SCR_INITIAL);
        price.setText(Integer.toString(GASOLINE_PRICE));
        stationID.setText(STATION_ID);
        announce.setEditable(false);
        amount.setText(Integer.toString(0));

        // code for display current time
        ActionListener taskPerformer = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                //System.out.println(currentTime());
                currentTime.setText(currentTime());
            }
        };
        Timer t = new Timer(1000, taskPerformer);
        t.start();
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
        stationID = new javax.swing.JLabel();
        currentTime = new javax.swing.JLabel();
        amount = new javax.swing.JLabel();
        price = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        btnQuickSelection1 = new javax.swing.JButton();
        btnQuickSelection2 = new javax.swing.JButton();
        btnQuickSelection3 = new javax.swing.JButton();
        btnQuickSelection4 = new javax.swing.JButton();
        btnQuickSelection5 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
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
        selectCard = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(204, 204, 255));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(stationID, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(amount, javax.swing.GroupLayout.DEFAULT_SIZE, 118, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(currentTime, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(price, javax.swing.GroupLayout.DEFAULT_SIZE, 108, Short.MAX_VALUE)))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(stationID, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(currentTime, javax.swing.GroupLayout.DEFAULT_SIZE, 20, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(amount, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(price, javax.swing.GroupLayout.DEFAULT_SIZE, 25, Short.MAX_VALUE)))
        );

        jPanel2.setBackground(new java.awt.Color(204, 204, 255));

        btnQuickSelection1.setText("1");
        btnQuickSelection1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnQuickSelection1ActionPerformed(evt);
            }
        });

        btnQuickSelection2.setText("2");
        btnQuickSelection2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnQuickSelection2ActionPerformed(evt);
            }
        });

        btnQuickSelection3.setText("3");
        btnQuickSelection3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnQuickSelection3ActionPerformed(evt);
            }
        });

        btnQuickSelection4.setText("4");
        btnQuickSelection4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnQuickSelection4ActionPerformed(evt);
            }
        });

        btnQuickSelection5.setText("5");
        btnQuickSelection5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnQuickSelection5ActionPerformed(evt);
            }
        });

        jLabel1.setText("20.000");

        jLabel2.setText("50.000");

        jLabel3.setText("100.000");

        jLabel4.setText("200.000");

        jLabel5.setText("500.000");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(btnQuickSelection3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(btnQuickSelection4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(btnQuickSelection5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(btnQuickSelection2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(btnQuickSelection1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 104, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnQuickSelection1)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(4, 4, 4)
                        .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnQuickSelection2)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(4, 4, 4)
                        .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnQuickSelection3)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(4, 4, 4)
                        .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(btnQuickSelection4)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(4, 4, 4)
                        .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnQuickSelection5)
                    .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                .addComponent(lblForBtn4, javax.swing.GroupLayout.DEFAULT_SIZE, 107, Short.MAX_VALUE)
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
                .addComponent(btnSubmit, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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

        selectCard.setText("Select Card");
        selectCard.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectCardActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, 113, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(2, 2, 2)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(selectCard)))
            .addGroup(layout.createSequentialGroup()
                .addGap(115, 115, 115)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addComponent(selectCard)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSelection1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelection1ActionPerformed
        // TODO add your handling code here:
        Apdu apdu = new Apdu();
        switch (screen) {
            case SCR_VALIDATE:
                setScreen(SCR_INITIAL);
                break;
            case SCR_VALIDATE_FAILED:
                setScreen(SCR_VALIDATE);
                break;
            case SCR_PIN_IS_BLOCKED:
                setScreen(SCR_INITIAL);
                break;
            case SCR_MAIN:
                setScreen(SCR_REFUEL);
                break;
            case SCR_GET_BALANCE:
                setScreen(SCR_MAIN);
                break;
            case SCR_GET_HISTORIES:
                setScreen(SCR_GET_HISTORIES_BY_TIME);
                break;
            case SCR_NAVIGATION:
                setScreen(backToScreen);
                break;
            case SCR_GET_HISTORIES_RESULT:
                try {
                    // write output text area to billFile
                    BufferedWriter fileOut = new BufferedWriter(new FileWriter(billFile));
                    announce.write(fileOut);
                    noticeString = "Please receive your bill";
                    backToScreen = SCR_MAIN;
                    setScreen(SCR_NAVIGATION);
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
                break;
            case SCR_REFUEL_CONFIRM:
                if (amountOfGasoline > maxAmount) {
                    noticeString = "Account balance is not enough!";
                    backToScreen = SCR_MAIN;
                    setScreen(SCR_NAVIGATION);
                    amountOfGasoline = 0;
                    amount.setText(Integer.toString(amountOfGasoline));
                    break;
                }
                maxAmount -= amountOfGasoline;
                // insert code to send update purchase info here
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = UPDATE_PURCHASE_INFO;
                try {
                    apdu.setDataIn(createUpdateInfo());
                    cad.exchangeApdu(apdu);
                } catch (IOException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                } catch (CadTransportException ex) {
                    Logger.getLogger(GasStation.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println(apdu);

                // if failed
                if (apdu.getStatus() != 0x9000) {
                    noticeString = "Error";
                    if (apdu.getStatus() == INVALID_STATION_SIGNATURE) {
                        noticeString = "Invalid station signature!";
                    }
                    backToScreen = SCR_INITIAL;
                    setScreen(SCR_NAVIGATION);
                    amountOfGasoline = 0;
                    amount.setText(Integer.toString(amountOfGasoline));
                    break;
                }
                // if successful
                if (apdu.getStatus() == 0x9000) {
                    apdu.command[CLA] = SSGS_CLA;
                    apdu.command[INS] = GET_LAST_PURCHASE_HISTORY;
                    apdu.setLc(0);
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
                        noticeString = "No result!";
                        backToScreen = SCR_MAIN;
                        setScreen(SCR_NAVIGATION);
                    }
                }
                amountOfGasoline = 0;
                amount.setText(Integer.toString(amountOfGasoline));
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

                // check if input is empty
                if (Lc == 0) {
                    noticeString = "PIN cannot empty!";
                    backToScreen = SCR_VALIDATE;
                    setScreen(SCR_NAVIGATION);
                    break;
                }

                for (int i = 0; i < Lc; i++) {
                    dataIn[i] = (byte) (inputArr[i] - '0');
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
                    noticeString = "Invalid format!";
                    backToScreen = SCR_GET_HISTORIES_BY_TIME;
                    setScreen(SCR_NAVIGATION);
                    break;
                }
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = GET_PURCHASE_HISTORIES_BY_TIME;
                dataIn = createByteArr(inputText.getText());
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
                    noticeString = "No result!";
                    backToScreen = SCR_MAIN;
                    setScreen(SCR_NAVIGATION);
                }
                break;
            case SCR_GET_HISTORIES_BY_STATION:
                // if input is valid
                if (inputText.getText().length() != 5) {
                    noticeString = "Invalid format!";
                    backToScreen = SCR_GET_HISTORIES_BY_STATION;
                    setScreen(SCR_NAVIGATION);
                    break;
                }
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = GET_PURCHASE_HISTORIES_BY_STATION;
                dataIn = createByteArr(inputText.getText());
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
                    noticeString = "No result!";
                    backToScreen = SCR_MAIN;
                    setScreen(SCR_NAVIGATION);
                }
                break;
            case SCR_CHANGE_PIN:
                newPIN = inputText.getText();
                setScreen(SCR_CONFIRM_PIN);
                break;
            case SCR_CONFIRM_PIN:
                confirmPIN = inputText.getText();
                if (!(newPIN.equals(confirmPIN))) {
                    noticeString = "PIN does not match with the confirm";
                    backToScreen = SCR_CHANGE_PIN;
                    setScreen(SCR_NAVIGATION);
                } else if (newPIN.length() > 8) {
                    noticeString = "New PIN length is too long";
                    backToScreen = SCR_CHANGE_PIN;
                    setScreen(SCR_NAVIGATION);
                } else if (newPIN.length() == 0) {
                    noticeString = "PIN cannot empty";
                    backToScreen = SCR_CHANGE_PIN;
                    setScreen(SCR_NAVIGATION);
                } else {
                    apdu.command[CLA] = SSGS_CLA;
                    apdu.command[INS] = CHANGE_PIN;

                    inputArr = newPIN.toCharArray();
                    Lc = inputArr.length;
                    for (int i = 0; i < Lc; i++) {
                        dataIn[i] = (byte) (inputArr[i] - '0');
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
                        noticeString = "PIN change successful";
                        backToScreen = SCR_MAIN;
                        setScreen(SCR_NAVIGATION);
                    }

                    if (apdu.getStatus() == SW_PIN_VERIFICATION_REQUIRED) {
                        noticeString = "Need validate PIN first";
                        backToScreen = SCR_VALIDATE;
                        setScreen(SCR_NAVIGATION);
                    }
                }

                break;
            case SCR_REFUEL:
                try {
                    amountOfGasoline = Integer.parseInt(inputText.getText());
                } catch (NumberFormatException e) {
                    amountOfGasoline = 0;
                    noticeString = "Invalid number";
                    backToScreen = SCR_REFUEL;
                    setScreen(SCR_NAVIGATION);
                    break;
                }
                if (amountOfGasoline <= 0) {
                    amountOfGasoline = 0;
                    noticeString = "Invalid number";
                    backToScreen = SCR_REFUEL;
                    setScreen(SCR_NAVIGATION);
                    break;
                }
                amount.setText(Integer.toString(amountOfGasoline));
                setScreen(SCR_REFUEL_CONFIRM);
                break;
        }
        inputText.setText(null);
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
        Apdu apdu = new Apdu();
        switch (screen) {
            case SCR_MAIN:
                setScreen(SCR_CHANGE_PIN);
                break;
            case SCR_GET_HISTORIES:
                apdu.command[CLA] = SSGS_CLA;
                apdu.command[INS] = GET_LAST_PURCHASE_HISTORY;
                apdu.setLc(0);
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
                    noticeString = "No result!";
                    backToScreen = SCR_MAIN;
                    setScreen(SCR_NAVIGATION);
                }
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

    private void btnQuickSelection5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnQuickSelection5ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_REFUEL:
                amountOfGasoline = 500000 / GASOLINE_PRICE;
                amount.setText(Integer.toString(amountOfGasoline));
                setScreen(SCR_REFUEL_CONFIRM);
                break;
        }
    }//GEN-LAST:event_btnQuickSelection5ActionPerformed

    private void btnQuickSelection1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnQuickSelection1ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_REFUEL:
                amountOfGasoline = 20000 / GASOLINE_PRICE;
                amount.setText(Integer.toString(amountOfGasoline));
                setScreen(SCR_REFUEL_CONFIRM);
                break;
        }
    }//GEN-LAST:event_btnQuickSelection1ActionPerformed

    private void btnQuickSelection2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnQuickSelection2ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_REFUEL:
                amountOfGasoline = 50000 / GASOLINE_PRICE;
                amount.setText(Integer.toString(amountOfGasoline));
                setScreen(SCR_REFUEL_CONFIRM);
                break;
        }
    }//GEN-LAST:event_btnQuickSelection2ActionPerformed

    private void btnQuickSelection3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnQuickSelection3ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_REFUEL:
                amountOfGasoline = 100000 / GASOLINE_PRICE;
                amount.setText(Integer.toString(amountOfGasoline));
                setScreen(SCR_REFUEL_CONFIRM);
                break;
        }
    }//GEN-LAST:event_btnQuickSelection3ActionPerformed

    private void btnQuickSelection4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnQuickSelection4ActionPerformed
        // TODO add your handling code here:
        switch (screen) {
            case SCR_REFUEL:
                amountOfGasoline = 200000 / GASOLINE_PRICE;
                amount.setText(Integer.toString(amountOfGasoline));
                setScreen(SCR_REFUEL_CONFIRM);
                break;
        }
    }//GEN-LAST:event_btnQuickSelection4ActionPerformed

    private void selectCardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectCardActionPerformed
        // TODO add your handling code here:
        try {
            switch (screen) {
                case SCR_INITIAL:
                    // code for select the card
                    int returnVal = fc.showOpenDialog(GasStation.this);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        // start cref
                        File card = fc.getSelectedFile();
                        String cmd = "E:\\Ariya\\JCDK3.0.4_ClassicEdition\\bin\\cref.bat -i \"" + card.getAbsolutePath() + "\" -o \"" + card.getAbsolutePath() + "\"";
                        System.out.println(cmd);
                        cref = Runtime.getRuntime().exec(cmd);
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

                        // if unsuccessful
                        if (apdu.getStatus() != 0x9000) {
                            noticeString = "Unable to connect the card";
                            backToScreen = SCR_INITIAL;
                            setScreen(SCR_NAVIGATION);
                            return;
                        }

                        // if successful
                        // code for get maxAmount
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
                        maxAmount = (int) balance / GASOLINE_PRICE;

                        setScreen(SCR_VALIDATE);
                        selectCard.setEnabled(false);
                    }
                    break;

            }
        } catch (Exception e) {
        }
    }//GEN-LAST:event_selectCardActionPerformed

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
    private javax.swing.JLabel amount;
    private javax.swing.JTextArea announce;
    private javax.swing.JButton btnQuickSelection1;
    private javax.swing.JButton btnQuickSelection2;
    private javax.swing.JButton btnQuickSelection3;
    private javax.swing.JButton btnQuickSelection4;
    private javax.swing.JButton btnQuickSelection5;
    private javax.swing.JButton btnSelection1;
    private javax.swing.JButton btnSelection2;
    private javax.swing.JButton btnSelection3;
    private javax.swing.JButton btnSelection4;
    private javax.swing.JButton btnSelection5;
    private javax.swing.JButton btnSubmit;
    private javax.swing.JLabel currentTime;
    private javax.swing.JTextField inputText;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
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
    private javax.swing.JLabel price;
    private javax.swing.JButton selectCard;
    private javax.swing.JLabel stationID;
    // End of variables declaration//GEN-END:variables
}

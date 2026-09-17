import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class MemoryCardGame {
    private static Map<String, List<HighScore>> highScoresByDifficulty = new HashMap<>();
    private static String playerName;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainMenu mainMenu = new MainMenu();
            mainMenu.setVisible(true);
        });
    }

    public static void setPlayerName(String name) {
        playerName = name;
    }

    public static String getPlayerName() {
        return playerName;
    }

    public static void addHighScore(String difficulty, HighScore highScore) {
        if (!highScoresByDifficulty.containsKey(difficulty)) {
            highScoresByDifficulty.put(difficulty, new ArrayList<>());
        }
        List<HighScore> highScores = highScoresByDifficulty.get(difficulty);
        highScores.add(highScore);
        Collections.sort(highScores);
        if (highScores.size() > 10) {
            highScores.remove(highScores.size() - 1);
        }
    }

    public static List<HighScore> getHighScores(String difficulty) {
        return highScoresByDifficulty.getOrDefault(difficulty, new ArrayList<>());
    }
}

class MainMenu extends JFrame implements ActionListener {
    private JButton startButton, highScoresButton;
    private JTextField nameField;

    public MainMenu() {
        setTitle("Memory Card Game");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center the window
        setLayout(new BorderLayout());

        // Create a panel for inputs and buttons
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);

        // Add a title label
        JLabel titleLabel = new JLabel("Memory Card Game");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(Box.createVerticalStrut(10));
        panel.add(titleLabel);

        // Add a name input field
        JLabel nameLabel = new JLabel("Enter your name:");
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(Box.createVerticalStrut(10));
        panel.add(nameLabel);

        nameField = new JTextField(20);
        nameField.setMaximumSize(new Dimension(300, 30));
        panel.add(Box.createVerticalStrut(5));
        panel.add(nameField);

        // Add the start button
        startButton = new JButton("Start Game");
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.addActionListener(e -> {
            String playerName = nameField.getText().trim();
            if (!playerName.isEmpty()) {
                MemoryCardGame.setPlayerName(playerName);
                DifficultySelection difficultySelection = new DifficultySelection();
                difficultySelection.setVisible(true);
                dispose(); // Close the main menu window
            } else {
                JOptionPane.showMessageDialog(this, "Please enter your name.", "Name Required",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(Box.createVerticalStrut(15));
        panel.add(startButton);

        // Add the high scores button
        highScoresButton = new JButton("High Scores");
        highScoresButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        highScoresButton.addActionListener(this);
        panel.add(Box.createVerticalStrut(10));
        panel.add(highScoresButton);

        // Add padding around the panel
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Add the panel to the frame
        add(panel, BorderLayout.CENTER);

        // Set the frame visible
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == highScoresButton) {
            // Display high scores
            HighScoresDialog highScoresDialog = new HighScoresDialog(this);
            highScoresDialog.setVisible(true);
        }
    }
}

class DifficultySelection extends JFrame implements ActionListener {
    private JButton easyButton, mediumButton, hardButton, homeButton;

    public DifficultySelection() {
        setTitle("Select Difficulty");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Create a panel for buttons
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        easyButton = createButton("Easy");
        mediumButton = createButton("Medium");
        hardButton = createButton("Hard");
        homeButton = createButton("Home");

        buttonPanel.add(easyButton);
        buttonPanel.add(mediumButton);
        buttonPanel.add(hardButton);
        buttonPanel.add(homeButton);

        add(buttonPanel, BorderLayout.CENTER);

        // Set the frame visible
        setVisible(true);
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.addActionListener(this);
        button.setFont(new Font("Arial", Font.BOLD, 18));
        return button;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == easyButton || e.getSource() == mediumButton || e.getSource() == hardButton) {
            int gridSize = 2;
            String difficulty = "";

            if (e.getSource() == easyButton) {
                gridSize = 2;
                difficulty = "Easy";
            } else if (e.getSource() == mediumButton) {
                gridSize = 4;
                difficulty = "Medium";
            } else if (e.getSource() == hardButton) {
                gridSize = 6;
                difficulty = "Hard";
            }

            MemoryCardGameWindow gameWindow = new MemoryCardGameWindow(MemoryCardGame.getPlayerName(), gridSize, difficulty);
            gameWindow.setVisible(true);
            dispose(); // Close the difficulty selection window
        } else if (e.getSource() == homeButton) {
            MainMenu mainMenu = new MainMenu();
            mainMenu.setVisible(true);
            dispose();
        }
    }
}


class MemoryCardGameWindow extends JFrame implements ActionListener {
    private JPanel cardPanel;
    private ArrayList<JButton> cards;
    private int gridSize;
    private int matchedPairs;
    private JButton prevCard;
    private JButton backButton; // Back button added
    private JLabel timerLabel; // Timer label added
    private int secondsLeft = 60; // Initial time in seconds
    private Timer timer; // Timer object
    private String playerName;
    private String difficulty;
    private String[] revealImagePath;
    private long startTime;

    private void setTimerDuration() {
        switch (difficulty) {
            case "Easy":
                secondsLeft = 60;
                break;
            case "Medium":
                secondsLeft = 60;
                break;
            case "Hard":
                secondsLeft = 60;
                break;
            default:
                secondsLeft = 60; // Default to 60 seconds if no difficulty matches
                break;
        }
    }

    public MemoryCardGameWindow(String playerName, int gridSize, String difficulty) {
        this.playerName = playerName;
        this.gridSize = gridSize;
        this.difficulty = difficulty;

        startTime = System.currentTimeMillis();

        setTitle("Memory Card Game");
        setSize(500, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center the window

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Panel for buttons
        cardPanel = new JPanel();
        add(cardPanel, BorderLayout.CENTER);

        createCards();
        matchedPairs = 0;

        backButton = new JButton("Back"); // Initialize backButton 
        buttonPanel.add(backButton); // Add backButton to buttonPanel

        // Set timer duration based on difficulty
        setTimerDuration();

        timerLabel = new JLabel("Time left: " + secondsLeft + " seconds"); // Initialize timerLabAel
        buttonPanel.add(timerLabel); // Add timerLabel to buttonPanel

        add(buttonPanel, BorderLayout.NORTH); // Add buttonPanel to the frame

        // Timer to update the time every second
        timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                secondsLeft--;
                timerLabel.setText("Time left: " + secondsLeft + " seconds");
                if (secondsLeft == 0) {
                    timer.stop(); // Stop the timer when time runs out
                    showGameOverDialog(true);
                }
            }
        });

        backButton.addActionListener(this);
        timer.start(); // Start the timer

        setVisible(true);
    }

    private void createCards() {
        cards = new ArrayList<>();
        cardPanel.removeAll();

        // Calculate the number of rows and columns for the grid layout
        int cols = gridSize;
        int rows = gridSize;

        cardPanel.setLayout(new GridLayout(rows, cols, 0, 0));
        cardPanel.setBackground(Color.WHITE);
        cardPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        ImageIcon backIcon;
        // Load the back icon based on difficulty level
        if (difficulty.equals("Easy")) {
            backIcon = new ImageIcon("images/easy.jpg");
        } else if (difficulty.equals("Medium")) {
            backIcon = new ImageIcon("images/medium.jpg");
        } else { // Hard difficulty
            backIcon = new ImageIcon("images/hard.jpg");
        }

        String imageSet;
        int numImages;
        if (gridSize == 2) {
            imageSet = "Images/easy/";
            numImages = 2;
        } else if (gridSize == 4) {
            imageSet = "Images/medium/";
            numImages = 8;
        } else { // gridSize == 6
            imageSet = "Images/hard/";
            numImages = 18;
        }

        // Initialize the revealImagePath array with the specified number of images
        revealImagePath = new String[numImages];

        // Populate the revealImagePath array using a for loop
        for (int i = 1; i <= numImages; i++) {
            revealImagePath[i - 1] = String.format("C:\\Users\\USER\\Desktop\\MemoryCardGame\\%s%d.png", imageSet, i);
        }

        int totalCards = gridSize * gridSize;
        int pairsCount = totalCards / 2;


        for (int i = 0; i < pairsCount; i++) {
            try {
                BufferedImage originalImage = ImageIO.read(new File(revealImagePath[i % revealImagePath.length]));
                ImageIcon scaledIcon = new ImageIcon(originalImage);
    
                // Create pairs of cards with the scaled icon
                JButton card1 = new JButton(backIcon);
                JButton card2 = new JButton(backIcon);

                card1.setDisabledIcon(scaledIcon);
                card2.setDisabledIcon(scaledIcon);

    
                // Set names for matching comparison
                card1.setName("card" + (i + 1));
                card2.setName("card" + (i + 1));
    
                // Add action listeners
                card1.addActionListener(this);
                card2.addActionListener(this);
    
                // Add cards to the card list
                cards.add(card1);
                cards.add(card2);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        // Shuffle the cards for randomness
        Collections.shuffle(cards);

        // Add cards to the card panel
        for (JButton card : cards) {
            cardPanel.add(card);
        }

        cardPanel.revalidate();
        cardPanel.repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == backButton) {
            timer.stop();
            secondsLeft = 60; // Reset the timer to 60 seconds
    
            DifficultySelection difficultySelection = new DifficultySelection();
            difficultySelection.setVisible(true);
            dispose(); 
            return;
        }

        JButton clickedCard = (JButton) e.getSource();
        clickedCard.setEnabled(false); // Disable the clicked card to prevent multiple clicks

        if (prevCard == null) {
            prevCard = clickedCard;
        } else {
            if (prevCard.getName().equals(clickedCard.getName())) {
                // Match found, disable both cards
                prevCard.setEnabled(false);
                clickedCard.setEnabled(false);
                prevCard = null; // Reset the previously clicked card
                matchedPairs++;
                if (matchedPairs == gridSize * gridSize / 2) {
                    timer.stop(); // Stop the timer when the game is completed
                    showGreatJobDialog(true);
                }
            } else {
                // No match, enable both cards after a short delay
                Timer delayTimer = new Timer(500, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        prevCard.setEnabled(true);
                        clickedCard.setEnabled(true);
                        prevCard = null; // Reset the previously clicked card
                    }
                });
                delayTimer.setRepeats(false);
                delayTimer.start();
            }
        }
    }

    private void showGameOverDialog(boolean enableBackButton) {
        JOptionPane.showMessageDialog(this, "Time's up! You lost.", "Game Over", JOptionPane.INFORMATION_MESSAGE);
        if (enableBackButton) {
            DifficultySelection difficultySelection = new DifficultySelection();
            difficultySelection.setVisible(true);
            dispose(); // Close the game window
        }
    }

    private void showGreatJobDialog(boolean enableBackButton) {
        long endTime = System.currentTimeMillis();
        long timeTaken = (endTime - startTime) / 1000; // Convert to seconds
        String message = "Great job, " + playerName + "! You completed the game in " + timeTaken + " seconds.";

        JOptionPane.showMessageDialog(this, message, "Congratulations!", JOptionPane.INFORMATION_MESSAGE);

        if (enableBackButton) {
            DifficultySelection difficultySelection = new DifficultySelection();
            difficultySelection.setVisible(true);
            dispose();

            // Add high score
            MemoryCardGame.addHighScore(difficulty, new HighScore(playerName, (int) timeTaken));
        }
    }
}

class HighScore implements Comparable<HighScore> {
    private final String playerName;
    private final int timeTaken;

    public HighScore(String playerName, int timeTaken) {
        this.playerName = playerName;
        this.timeTaken = timeTaken;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getTimeTaken() {
        return timeTaken;
    }

    @Override
    public int compareTo(HighScore other) {
        // Compare based on time taken (ascending order)
        return Integer.compare(this.timeTaken, other.timeTaken);
    }
}

class HighScoresDialog extends JDialog {
    public HighScoresDialog(JFrame parent) {
        super(parent, "High Scores", true);
        setSize(300, 300);
        setLocationRelativeTo(parent);
        
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBackground(Color.WHITE);

        for (String difficulty : new String[]{"Easy", "Medium", "Hard"}) {
            List<HighScore> highScores = MemoryCardGame.getHighScores(difficulty);
            JTextArea scoresArea = new JTextArea();
            scoresArea.setEditable(false);
            scoresArea.setFont(new Font("Arial", Font.PLAIN, 14));
            scoresArea.append(difficulty + ":\n");
            for (int i = 0; i < highScores.size(); i++) {
                HighScore highScore = highScores.get(i);
                scoresArea.append((i + 1) + ". " + highScore.getPlayerName() + ": " + highScore.getTimeTaken() + " seconds\n");
            }
            JScrollPane scrollPane = new JScrollPane(scoresArea);
            panel.add(scrollPane);
        }

        getContentPane().add(panel, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.setFont(new Font("Arial", Font.PLAIN, 16));
        closeButton.addActionListener(e -> dispose());
        
        // Add padding around the button
        closeButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        getContentPane().add(closeButton, BorderLayout.SOUTH);
    }
}


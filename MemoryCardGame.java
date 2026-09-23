import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Memory Card Game (improved).
 *
 * - One window with screen switching (no more opening/closing JFrames)
 * - Images are loaded from a relative "images" folder; if an image is missing,
 *   a colored lettered card is drawn instead, so the game always works
 * - Flip animation, matched-card highlight, move counter, time bar
 * - Input lock while two unmatched cards are showing (fixes the 3rd-click bug)
 * - Different time limits per difficulty
 * - High scores are saved to highscores.csv and survive restarts
 *
 * Optional images (relative to where you run the game):
 *   images/easy.jpg, images/medium.jpg, images/hard.jpg   -> card backs
 *   images/easy/1.png ... 2.png                           -> Easy faces
 *   images/medium/1.png ... 8.png                         -> Medium faces
 *   images/hard/1.png ... 18.png                          -> Hard faces
 */
public class MemoryCardGame extends JFrame {
    private static final String MENU = "menu";
    private static final String DIFFICULTY = "difficulty";
    private static final String GAME = "game";

    private final CardLayout screens = new CardLayout();
    private final JPanel root = new JPanel(screens);
    private final HighScoreManager scores = new HighScoreManager(Paths.get("highscores.csv"));
    private final MenuPanel menuPanel;
    private final DifficultyPanel difficultyPanel;
    private GamePanel gamePanel;
    private String playerName = "";

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> new MemoryCardGame().setVisible(true));
    }

    public MemoryCardGame() {
        super("Memory Card Game");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(520, 620));
        setSize(640, 720);
        setLocationRelativeTo(null);

        menuPanel = new MenuPanel(this);
        difficultyPanel = new DifficultyPanel(this);
        root.add(menuPanel, MENU);
        root.add(difficultyPanel, DIFFICULTY);
        setContentPane(root);

        showMenu();
    }

    void showMenu() {
        stopGame();
        screens.show(root, MENU);
        menuPanel.focusName();
    }

    void showDifficulty() {
        stopGame();
        difficultyPanel.refresh();
        screens.show(root, DIFFICULTY);
    }

    void startGame(Difficulty difficulty) {
        stopGame();
        gamePanel = new GamePanel(this, difficulty);
        root.add(gamePanel, GAME);
        screens.show(root, GAME);
        root.revalidate();
        root.repaint();
    }

    private void stopGame() {
        if (gamePanel != null) {
            gamePanel.dispose();
            root.remove(gamePanel);
            gamePanel = null;
        }
    }

    HighScoreManager getScores() { return scores; }
    String getPlayerName() { return playerName; }
    void setPlayerName(String name) { playerName = name; }
}

/* ============================ Settings & helpers ============================ */

enum Difficulty {
    EASY("Easy", 2, 30),
    MEDIUM("Medium", 4, 90),
    HARD("Hard", 6, 180);

    final String label;
    final int grid;
    final int seconds;

    Difficulty(String label, int grid, int seconds) {
        this.label = label;
        this.grid = grid;
        this.seconds = seconds;
    }

    int pairs() { return grid * grid / 2; }
    String folder() { return label.toLowerCase(Locale.ROOT); }
}

final class Theme {
    static final Color BG = new Color(0x1E1B2E);
    static final Color PANEL = new Color(0x2A2640);
    static final Color PANEL_LIGHT = new Color(0x3A3558);
    static final Color ACCENT = new Color(0x7C5CFF);
    static final Color TEAL = new Color(0x1FA99C);
    static final Color DANGER = new Color(0xE0486A);
    static final Color TEXT = new Color(0xF4F2FF);
    static final Color MUTED = new Color(0xA6A1C2);
    static final Color MATCHED = new Color(0x2EE6A8);

    static Font font(int style, int size) { return new Font("SansSerif", style, size); }

    private Theme() {}
}

final class UI {
    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    static void fixSize(JComponent c, int w, int h) {
        Dimension d = new Dimension(w, h);
        c.setPreferredSize(d);
        c.setMaximumSize(d);
        c.setMinimumSize(d);
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    static String clock(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }

    static void drawCentered(Graphics2D g2, String s, Font f, int size) {
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        int x = (size - fm.stringWidth(s)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(s, x, y);
    }

    private UI() {}
}

/** Loads images from disk (relative path) or the classpath, with caching. Returns null if not found. */
final class Assets {
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    static BufferedImage load(String... paths) {
        String key = String.join("|", paths);
        if (CACHE.containsKey(key)) return CACHE.get(key);
        if (MISSING.contains(key)) return null;

        for (String p : paths) {
            try {
                File f = new File(p);
                if (f.isFile()) {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) { CACHE.put(key, img); return img; }
                }
                URL url = MemoryCardGame.class.getResource("/" + p);
                if (url != null) {
                    BufferedImage img = ImageIO.read(url);
                    if (img != null) { CACHE.put(key, img); return img; }
                }
            } catch (IOException ex) {
                System.err.println("Could not read image " + p + ": " + ex.getMessage());
            }
        }
        MISSING.add(key);
        return null;
    }

    private Assets() {}
}

/* ================================ Widgets ================================ */

class RoundedButton extends JButton {
    private final Color base;
    private boolean hover;

    RoundedButton(String text, Color base) {
        super(text);
        this.base = base;
        setFont(Theme.font(Font.BOLD, 16));
        setForeground(Theme.TEXT);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color c = base;
        if (!isEnabled()) c = base.darker().darker();
        else if (getModel().isPressed()) c = base.darker();
        else if (hover) c = lighten(base, 25);
        g2.setColor(c);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        g2.dispose();
        super.paintComponent(g);
    }

    private static Color lighten(Color c, int amt) {
        return new Color(Math.min(255, c.getRed() + amt),
                Math.min(255, c.getGreen() + amt),
                Math.min(255, c.getBlue() + amt));
    }
}

/** A single card, drawn by hand so it scales to any grid size and can animate. */
class Card extends JComponent {
    private static final int ARC = 18;
    private static final int FLIP_MS = 220;

    private final int pairId;
    private final BufferedImage faceImage;
    private final BufferedImage backImage;
    private final Color faceColor;
    private final String faceText;

    private boolean faceUp;
    private boolean matched;
    private boolean showingFront;
    private boolean hover;
    private double flipScale = 1.0;
    private Timer animation;

    Card(int pairId, BufferedImage faceImage, Color faceColor, String faceText,
         BufferedImage backImage, Consumer<Card> onClick) {
        this.pairId = pairId;
        this.faceImage = faceImage;
        this.faceColor = faceColor;
        this.faceText = faceText;
        this.backImage = backImage;

        setPreferredSize(new Dimension(90, 90));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) onClick.accept(Card.this);
            }
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    int getPairId() { return pairId; }
    boolean isFaceUp() { return faceUp; }
    boolean isMatched() { return matched; }

    void setMatched(boolean matched) {
        this.matched = matched;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    void flip(boolean up) {
        if (faceUp == up) return;
        faceUp = up;
        stopAnimation();
        final long start = System.nanoTime();
        animation = new Timer(15, e -> {
            double t = Math.min(1.0, (System.nanoTime() - start) / 1_000_000.0 / FLIP_MS);
            if (t < 0.5) {
                flipScale = 1.0 - t * 2;          // shrink old side
            } else {
                showingFront = faceUp;            // swap side at the midpoint
                flipScale = (t - 0.5) * 2;        // grow new side
            }
            if (t >= 1.0) {
                flipScale = 1.0;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        animation.start();
    }

    void stopAnimation() {
        if (animation != null) animation.stop();
        showingFront = faceUp;
        flipScale = 1.0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        int size = Math.min(getWidth(), getHeight()) - 6;
        if (size <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Center the square card and squash it horizontally for the flip effect
            g2.translate(getWidth() / 2.0, getHeight() / 2.0);
            g2.scale(Math.max(0.02, flipScale), 1.0);
            g2.translate(-size / 2.0, -size / 2.0);

            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, size, size, ARC, ARC);

            g2.setColor(new Color(0, 0, 0, 70)); // shadow
            g2.fill(new RoundRectangle2D.Double(2, 3, size, size, ARC, ARC));

            Shape oldClip = g2.getClip();
            if (showingFront) paintFront(g2, shape, size);
            else paintBack(g2, shape, size);
            g2.setClip(oldClip);

            g2.setStroke(new BasicStroke(matched ? 4f : 2f));
            g2.setColor(matched ? Theme.MATCHED : new Color(255, 255, 255, 45));
            g2.draw(new RoundRectangle2D.Double(1, 1, size - 2, size - 2, ARC, ARC));
        } finally {
            g2.dispose();
        }
    }

    private void paintBack(Graphics2D g2, RoundRectangle2D shape, int size) {
        if (backImage != null) {
            g2.clip(shape);
            g2.drawImage(backImage, 0, 0, size, size, null);
        } else {
            g2.setPaint(new GradientPaint(0, 0, Theme.ACCENT, size, size, Theme.ACCENT.darker().darker()));
            g2.fill(shape);
            g2.clip(shape);
            g2.setColor(new Color(255, 255, 255, 25));
            for (int i = 0; i < size * 2; i += 14) {
                g2.drawLine(i, 0, i - size, size);
            }
            g2.setColor(new Color(255, 255, 255, 210));
            UI.drawCentered(g2, "?", Theme.font(Font.BOLD, size / 2), size);
        }
        if (hover && !faceUp && !matched) {
            g2.setColor(new Color(255, 255, 255, 35));
            g2.fill(shape);
        }
    }

    private void paintFront(Graphics2D g2, RoundRectangle2D shape, int size) {
        if (faceImage != null) {
            g2.setColor(Color.WHITE);
            g2.fill(shape);
            g2.clip(shape);
            int pad = Math.max(4, size / 12);
            double avail = size - 2.0 * pad;
            double scale = Math.min(avail / faceImage.getWidth(), avail / faceImage.getHeight());
            int w = (int) (faceImage.getWidth() * scale);
            int h = (int) (faceImage.getHeight() * scale);
            g2.drawImage(faceImage, (size - w) / 2, (size - h) / 2, w, h, null);
        } else {
            g2.setColor(faceColor);
            g2.fill(shape);
            g2.setColor(Color.WHITE);
            UI.drawCentered(g2, faceText, Theme.font(Font.BOLD, size / 2), size);
        }
    }
}

/* ================================ Screens ================================ */

class MenuPanel extends JPanel {
    private final JTextField nameField = new JTextField(18);

    MenuPanel(MemoryCardGame game) {
        super(new GridBagLayout());
        setBackground(Theme.BG);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);

        box.add(UI.label("Memory Card Game", Font.BOLD, 34, Theme.TEXT));
        box.add(Box.createVerticalStrut(6));
        box.add(UI.label("Flip. Remember. Match.", Font.PLAIN, 15, Theme.MUTED));
        box.add(Box.createVerticalStrut(36));
        box.add(UI.label("Enter your name", Font.PLAIN, 14, Theme.MUTED));
        box.add(Box.createVerticalStrut(8));

        nameField.setFont(Theme.font(Font.PLAIN, 16));
        nameField.setForeground(Theme.TEXT);
        nameField.setBackground(Theme.PANEL);
        nameField.setCaretColor(Theme.TEXT);
        nameField.setHorizontalAlignment(JTextField.CENTER);
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.PANEL_LIGHT, 2),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        UI.fixSize(nameField, 260, 42);
        nameField.addActionListener(e -> submit(game)); // Enter key starts the game
        box.add(nameField);
        box.add(Box.createVerticalStrut(24));

        RoundedButton start = new RoundedButton("Start Game", Theme.ACCENT);
        RoundedButton scores = new RoundedButton("High Scores", Theme.PANEL_LIGHT);
        RoundedButton exit = new RoundedButton("Exit", Theme.PANEL);
        for (RoundedButton b : new RoundedButton[]{start, scores, exit}) {
            UI.fixSize(b, 260, 46);
            box.add(b);
            box.add(Box.createVerticalStrut(10));
        }
        start.addActionListener(e -> submit(game));
        scores.addActionListener(e -> new HighScoresDialog(game, game.getScores()).setVisible(true));
        exit.addActionListener(e -> game.dispose());

        add(box);
    }

    private void submit(MemoryCardGame game) {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your name.", "Name Required",
                    JOptionPane.WARNING_MESSAGE);
            nameField.requestFocusInWindow();
            return;
        }
        if (name.length() > 20) name = name.substring(0, 20);
        game.setPlayerName(name);
        game.showDifficulty();
    }

    void focusName() {
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);
    }
}

class DifficultyPanel extends JPanel {
    private final MemoryCardGame game;
    private final JLabel greeting = UI.label("", Font.BOLD, 28, Theme.TEXT);
    private final Map<Difficulty, JLabel> bestLabels = new EnumMap<>(Difficulty.class);

    DifficultyPanel(MemoryCardGame game) {
        super(new GridBagLayout());
        this.game = game;
        setBackground(Theme.BG);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);

        box.add(greeting);
        box.add(Box.createVerticalStrut(6));
        box.add(UI.label("Choose a difficulty", Font.PLAIN, 15, Theme.MUTED));
        box.add(Box.createVerticalStrut(28));

        Color[] colors = {Theme.TEAL, Theme.ACCENT, Theme.DANGER};
        for (Difficulty d : Difficulty.values()) {
            String text = d.label + "   " + d.grid + "x" + d.grid + "   " + UI.clock(d.seconds);
            RoundedButton b = new RoundedButton(text, colors[d.ordinal()]);
            UI.fixSize(b, 300, 50);
            b.addActionListener(e -> game.startGame(d));
            box.add(b);
            box.add(Box.createVerticalStrut(4));

            JLabel best = UI.label(" ", Font.PLAIN, 12, Theme.MUTED);
            bestLabels.put(d, best);
            box.add(best);
            box.add(Box.createVerticalStrut(14));
        }

        RoundedButton home = new RoundedButton("Home", Theme.PANEL_LIGHT);
        UI.fixSize(home, 300, 44);
        home.addActionListener(e -> game.showMenu());
        box.add(Box.createVerticalStrut(8));
        box.add(home);

        add(box);
    }

    void refresh() {
        greeting.setText("Hi, " + game.getPlayerName() + "!");
        for (Difficulty d : Difficulty.values()) {
            List<HighScore> list = game.getScores().get(d);
            bestLabels.get(d).setText(list.isEmpty()
                    ? "No scores yet"
                    : "Best: " + list.get(0).name + " - " + UI.seconds(list.get(0).millis)
                      + ", " + list.get(0).moves + " moves");
        }
    }
}

class GamePanel extends JPanel {
    private final MemoryCardGame game;
    private final Difficulty difficulty;
    private final List<Card> cards = new ArrayList<>();

    private final JLabel timeLabel = UI.label("", Font.BOLD, 15, Theme.TEXT);
    private final JLabel movesLabel = UI.label("", Font.BOLD, 15, Theme.TEXT);
    private final JLabel pairsLabel = UI.label("", Font.BOLD, 15, Theme.TEXT);
    private final JLabel hintLabel = UI.label("The timer starts on your first flip.", Font.PLAIN, 13, Theme.MUTED);
    private final JProgressBar timeBar;
    private final Timer countdown;
    private Timer pendingTimer;

    private Card first;
    private boolean busy;      // true while a mismatched pair is still showing
    private boolean started;
    private boolean finished;
    private int secondsLeft;
    private int moves;
    private int matchedPairs;
    private long startNanos;

    GamePanel(MemoryCardGame game, Difficulty difficulty) {
        super(new BorderLayout());
        this.game = game;
        this.difficulty = difficulty;
        this.secondsLeft = difficulty.seconds;
        setBackground(Theme.BG);

        // --- Top bar: Back | title | Restart
        RoundedButton back = new RoundedButton("Back", Theme.PANEL_LIGHT);
        RoundedButton restart = new RoundedButton("Restart", Theme.ACCENT);
        back.setFont(Theme.font(Font.BOLD, 14));
        restart.setFont(Theme.font(Font.BOLD, 14));
        back.addActionListener(e -> leave());
        restart.addActionListener(e -> game.startGame(difficulty));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(back, BorderLayout.WEST);
        topRow.add(UI.label(game.getPlayerName() + "  |  " + difficulty.label, Font.BOLD, 17, Theme.TEXT)
                , BorderLayout.CENTER);
        ((JLabel) topRow.getComponent(1)).setHorizontalAlignment(SwingConstants.CENTER);
        topRow.add(restart, BorderLayout.EAST);

        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 28, 0));
        statsRow.setOpaque(false);
        statsRow.add(timeLabel);
        statsRow.add(movesLabel);
        statsRow.add(pairsLabel);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(Theme.PANEL);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        header.add(topRow);
        header.add(Box.createVerticalStrut(10));
        header.add(statsRow);

        timeBar = new JProgressBar(0, difficulty.seconds);
        timeBar.setBorderPainted(false);
        timeBar.setBackground(Theme.PANEL);
        timeBar.setPreferredSize(new Dimension(10, 6));

        JPanel north = new JPanel(new BorderLayout());
        north.add(header, BorderLayout.CENTER);
        north.add(timeBar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // --- Board
        JPanel board = new JPanel(new GridLayout(difficulty.grid, difficulty.grid, 8, 8));
        board.setBackground(Theme.BG);
        board.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        buildCards();
        for (Card c : cards) board.add(c);
        add(board, BorderLayout.CENTER);

        hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 12, 0));
        add(hintLabel, BorderLayout.SOUTH);

        countdown = new Timer(1000, e -> tick());
        updateStats();
    }

    private void buildCards() {
        String folder = difficulty.folder();
        BufferedImage back = Assets.load("images/" + folder + ".jpg", "Images/" + folder + ".jpg",
                "images/" + folder + ".png");

        for (int i = 0; i < difficulty.pairs(); i++) {
            int n = i + 1;
            BufferedImage face = Assets.load("images/" + folder + "/" + n + ".png",
                    "Images/" + folder + "/" + n + ".png");
            Color color = Color.getHSBColor((float) i / difficulty.pairs(), 0.6f, 0.85f);
            String text = String.valueOf((char) ('A' + i));
            // Two cards per pair, same pairId
            cards.add(new Card(i, face, color, text, back, this::onCardClicked));
            cards.add(new Card(i, face, color, text, back, this::onCardClicked));
        }
        Collections.shuffle(cards);
    }

    private void onCardClicked(Card card) {
        if (busy || finished || card.isFaceUp() || card.isMatched()) return;

        if (!started) {
            started = true;
            startNanos = System.nanoTime();
            countdown.start();
            hintLabel.setText(" ");
        }

        card.flip(true);

        if (first == null) {
            first = card;
            return;
        }

        Card second = card;
        moves++;

        if (first.getPairId() == second.getPairId()) {
            first.setMatched(true);
            second.setMatched(true);
            first = null;
            matchedPairs++;
            updateStats();
            if (matchedPairs == difficulty.pairs()) win();
        } else {
            busy = true; // block extra clicks until these two flip back
            updateStats();
            Card a = first;
            pendingTimer = new Timer(750, e -> {
                a.flip(false);
                second.flip(false);
                first = null;
                busy = false;
            });
            pendingTimer.setRepeats(false);
            pendingTimer.start();
        }
    }

    private void tick() {
        secondsLeft--;
        updateStats();
        if (secondsLeft <= 0) lose();
    }

    private void updateStats() {
        boolean low = secondsLeft <= 10;
        timeLabel.setText("Time " + UI.clock(Math.max(0, secondsLeft)));
        timeLabel.setForeground(low ? Theme.DANGER : Theme.TEXT);
        movesLabel.setText("Moves " + moves);
        pairsLabel.setText("Pairs " + matchedPairs + "/" + difficulty.pairs());
        timeBar.setValue(Math.max(0, secondsLeft));
        timeBar.setForeground(low ? Theme.DANGER : Theme.MATCHED);
    }

    private void win() {
        finished = true;
        countdown.stop();
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        String name = game.getPlayerName();
        int rank = game.getScores().add(difficulty, new HighScore(name, millis, moves));

        String msg = "Great job, " + name + "!\n\n"
                + "Time: " + UI.seconds(millis) + "\n"
                + "Moves: " + moves;
        if (rank == 1) msg += "\n\nNew best time on " + difficulty.label + "!";
        else if (rank > 0) msg += "\n\nYou placed #" + rank + " on the " + difficulty.label + " leaderboard.";

        showEndDialogLater("You win!", msg, JOptionPane.INFORMATION_MESSAGE);
    }

    private void lose() {
        finished = true;
        countdown.stop();
        if (pendingTimer != null) pendingTimer.stop();
        for (Card c : cards) {
            if (!c.isMatched()) c.flip(true); // reveal the remaining cards
        }
        showEndDialogLater("Time's up!",
                "Time's up, " + game.getPlayerName() + "!\nYou matched "
                        + matchedPairs + " of " + difficulty.pairs() + " pairs.",
                JOptionPane.WARNING_MESSAGE);
    }

    /** Wait for the last flip animation to finish, then show the result. */
    private void showEndDialogLater(String title, String msg, int type) {
        Timer t = new Timer(400, e -> {
            Object[] options = {"Play Again", "Change Difficulty", "Main Menu"};
            int choice = JOptionPane.showOptionDialog(this, msg, title, JOptionPane.DEFAULT_OPTION,
                    type, null, options, options[0]);
            if (choice == 0) game.startGame(difficulty);
            else if (choice == 1) game.showDifficulty();
            else if (choice == 2) game.showMenu();
            // closing the dialog keeps the finished board visible; Back/Restart still work
        });
        t.setRepeats(false);
        t.start();
    }

    private void leave() {
        if (started && !finished) {
            countdown.stop(); // pause while asking
            int c = JOptionPane.showConfirmDialog(this,
                    "Leave this game? Your progress will be lost.", "Leave Game",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (c != JOptionPane.YES_OPTION) {
                countdown.start();
                return;
            }
        }
        game.showDifficulty();
    }

    /** Stop every timer so nothing keeps running after the screen is gone. */
    void dispose() {
        countdown.stop();
        if (pendingTimer != null) pendingTimer.stop();
        for (Card c : cards) c.stopAnimation();
    }
}

/* ============================== High scores ============================== */

final class HighScore implements Comparable<HighScore> {
    final String name;
    final long millis;
    final int moves;

    HighScore(String name, long millis, int moves) {
        this.name = name;
        this.millis = millis;
        this.moves = moves;
    }

    @Override
    public int compareTo(HighScore other) {
        int byTime = Long.compare(millis, other.millis);
        return byTime != 0 ? byTime : Integer.compare(moves, other.moves); // tie-break on moves
    }
}

/** Keeps the top 10 per difficulty and saves them to a CSV file. */
final class HighScoreManager {
    private static final int MAX = 10;
    private final Path file;
    private final Map<Difficulty, List<HighScore>> scores = new EnumMap<>(Difficulty.class);

    HighScoreManager(Path file) {
        this.file = file;
        for (Difficulty d : Difficulty.values()) scores.put(d, new ArrayList<>());
        load();
    }

    List<HighScore> get(Difficulty d) {
        return Collections.unmodifiableList(scores.get(d));
    }

    /** Adds a score and returns its rank (1 = best), or -1 if it didn't make the top 10. */
    int add(Difficulty d, HighScore score) {
        List<HighScore> list = scores.get(d);
        list.add(score);
        Collections.sort(list);
        while (list.size() > MAX) list.remove(list.size() - 1);
        int index = list.indexOf(score);
        save();
        return index >= 0 ? index + 1 : -1;
    }

    void clearAll() {
        for (List<HighScore> list : scores.values()) list.clear();
        save();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                // Format: DIFFICULTY,millis,moves,name  (name last so it may contain commas)
                String[] parts = line.split(",", 4);
                if (parts.length < 4) continue;
                try {
                    Difficulty d = Difficulty.valueOf(parts[0]);
                    scores.get(d).add(new HighScore(parts[3],
                            Long.parseLong(parts[1]), Integer.parseInt(parts[2])));
                } catch (IllegalArgumentException ignored) {
                    // skip bad lines instead of crashing
                }
            }
            for (List<HighScore> list : scores.values()) Collections.sort(list);
        } catch (IOException ex) {
            System.err.println("Could not load high scores: " + ex.getMessage());
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Difficulty, List<HighScore>> e : scores.entrySet()) {
            for (HighScore s : e.getValue()) {
                lines.add(e.getKey().name() + "," + s.millis + "," + s.moves + "," + s.name);
            }
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("Could not save high scores: " + ex.getMessage());
        }
    }
}

class HighScoresDialog extends JDialog {
    HighScoresDialog(JFrame owner, HighScoreManager scores) {
        super(owner, "High Scores", true);
        setSize(460, 400);
        setLocationRelativeTo(owner);

        JTabbedPane tabs = new JTabbedPane();
        Map<Difficulty, DefaultTableModel> models = new EnumMap<>(Difficulty.class);

        for (Difficulty d : Difficulty.values()) {
            DefaultTableModel model = new DefaultTableModel(new Object[]{"#", "Player", "Time", "Moves"}, 0) {
                @Override public boolean isCellEditable(int row, int col) { return false; }
            };
            models.put(d, model);

            JTable table = new JTable(model);
            table.setRowHeight(26);
            table.setFont(Theme.font(Font.PLAIN, 14));
            table.setBackground(Theme.PANEL);
            table.setForeground(Theme.TEXT);
            table.setGridColor(Theme.PANEL_LIGHT);
            table.setSelectionBackground(Theme.ACCENT);
            table.setSelectionForeground(Theme.TEXT);
            table.setFillsViewportHeight(true);
            table.getTableHeader().setReorderingAllowed(false);
            table.getColumnModel().getColumn(0).setMaxWidth(40);

            JScrollPane scroll = new JScrollPane(table);
            scroll.getViewport().setBackground(Theme.PANEL);
            tabs.addTab(d.label, scroll);
        }

        Runnable fill = () -> {
            for (Difficulty d : Difficulty.values()) {
                DefaultTableModel model = models.get(d);
                model.setRowCount(0);
                List<HighScore> list = scores.get(d);
                for (int i = 0; i < list.size(); i++) {
                    HighScore s = list.get(i);
                    model.addRow(new Object[]{i + 1, s.name, UI.seconds(s.millis), s.moves});
                }
            }
        };
        fill.run();

        RoundedButton clear = new RoundedButton("Clear All", Theme.DANGER);
        RoundedButton close = new RoundedButton("Close", Theme.ACCENT);
        clear.setFont(Theme.font(Font.BOLD, 14));
        close.setFont(Theme.font(Font.BOLD, 14));
        clear.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Delete all high scores?", "Clear Scores",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (c == JOptionPane.YES_OPTION) {
                scores.clearAll();
                fill.run();
            }
        });
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setBackground(Theme.BG);
        buttons.add(clear);
        buttons.add(close);

        getContentPane().setBackground(Theme.BG);
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
    }
}
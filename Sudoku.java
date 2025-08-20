import java.awt.*;          // Importing AWT package for GUI components
import java.awt.event.*;      // Importing AWT event package for handling events
import java.util.ArrayList;   // For shuffling numbers
import java.util.Collections; // For shuffling numbers
import java.util.List;        // For shuffling numbers
import java.util.Random;        // Importing Random for generating random numbers

class Sudoku extends Frame implements ActionListener {
    private static final int SIZE = 9;                  // Size of the Sudoku grid
    private static final int SUBGRID_SIZE = 3;          // Size of each 3x3 sub-grid
    private TextField[][] cells = new TextField[SIZE][SIZE]; // 2D array for text fields
    private int[][] sudoku = new int[SIZE][SIZE];      // 2D array to store the Sudoku puzzle (initial state)
    private int[][] solution = new int[SIZE][SIZE];    // 2D array to store the complete solution
    private Button checkButton, resetButton, endButton, solutionButton; // Buttons for user actions
    private TextField speedField;                       // Field to control visualization speed
    private Label speedLabel;                           // Label for the speed field

    private volatile boolean solving = false;           // Flag to indicate if solver is running
    private SolveThread solverThread = null;           // Thread for the visualization

    // Colors for visualization
    private final Color ORIGINAL_BG_COLOR = Color.WHITE;
    private final Color FILLED_BG_COLOR = Color.LIGHT_GRAY; // Color for initially filled cells
    private final Color SOLVING_BG_COLOR = Color.YELLOW;   // Color for cell being tried
    private final Color BACKTRACK_BG_COLOR = Color.ORANGE; // Color for cell during backtrack
    private final Color FINAL_SOLVE_COLOR = Color.GREEN;  // Color for correctly placed number during solve


    public Sudoku() {
        setTitle("Sudoku Game");        // Set the title of the window
        setSize(500, 550);              // Increased size slightly for new controls
        setLayout(new BorderLayout());  // Set layout manager

        // Create a panel for the Sudoku grid with 3x3 grid gaps
        Panel mainGridPanel = new Panel(new GridLayout(SUBGRID_SIZE, SUBGRID_SIZE, 5, 5));
        mainGridPanel.setBackground(Color.DARK_GRAY); // Set main grid background color

        // Create 3x3 subgrids for the Sudoku puzzle
        for (int subRow = 0; subRow < SUBGRID_SIZE; subRow++) {
            for (int subCol = 0; subCol < SUBGRID_SIZE; subCol++) {
                Panel subGridPanel = new Panel(new GridLayout(SUBGRID_SIZE, SUBGRID_SIZE)); // Each 3x3 sub-grid
                // No background needed here if main grid has one

                // Initialize cells in each 3x3 sub-grid
                for (int row = subRow * SUBGRID_SIZE; row < (subRow + 1) * SUBGRID_SIZE; row++) {
                    for (int col = subCol * SUBGRID_SIZE; col < (subCol + 1) * SUBGRID_SIZE; col++) {
                        cells[row][col] = new TextField(); // Create a new text field
                        cells[row][col].setFont(new Font("SansSerif", Font.BOLD, 20)); // Increased font size
                        cells[row][col].setPreferredSize(new Dimension(40, 40)); // Increased cell size
                        cells[row][col].setBackground(ORIGINAL_BG_COLOR); // Set background color for the cell
                        cells[row][col].setEditable(true); // Allow editing initially
                        cells[row][col].addKeyListener(new KeyAdapter() { // Limit input to 1-9
                            public void keyTyped(KeyEvent e) {
                                char c = e.getKeyChar();
                                TextField source = (TextField) e.getSource();
                                String currentText = source.getText();
                                // Allow digits 1-9, backspace, delete
                                if (!((c >= '1' && c <= '9') || c == KeyEvent.VK_BACK_SPACE || c == KeyEvent.VK_DELETE)) {
                                    e.consume(); // Ignore invalid characters
                                } else if (Character.isDigit(c) && currentText.length() >= 1 && source.getSelectedText() == null) {
                                    // If already has a digit and no text is selected, replace the digit
                                    source.setText(String.valueOf(c));
                                     e.consume(); // Consume the event after setting text manually
                                }
                                 // Allow backspace/delete regardless of length
                            }
                        });
                        subGridPanel.add(cells[row][col]); // Add the cell to the sub-grid
                    }
                }
                mainGridPanel.add(subGridPanel); // Add sub-grid to the main grid panel
            }
        }

        // Create action buttons and controls in a separate panel
        Panel controlPanel = new Panel(new FlowLayout(FlowLayout.CENTER, 10, 10)); // Panel for buttons and speed
        checkButton = new Button("Check");
        checkButton.addActionListener(this);
        resetButton = new Button("Reset");
        resetButton.addActionListener(this);
        solutionButton = new Button("Solution"); // New Solution button
        solutionButton.addActionListener(this);
        endButton = new Button("End");
        endButton.addActionListener(this);

        speedLabel = new Label("Speed (ms):");
        speedField = new TextField("100", 4); // Default 100ms delay, width 4

        controlPanel.add(checkButton);
        controlPanel.add(resetButton);
        controlPanel.add(solutionButton); // Add solution button
        controlPanel.add(speedLabel);     // Add speed label
        controlPanel.add(speedField);     // Add speed field
        controlPanel.add(endButton);

        // Add panels to the main frame
        add(mainGridPanel, BorderLayout.CENTER); // Add Sudoku grid to center
        add(controlPanel, BorderLayout.SOUTH);  // Add control panel to south

        // Generate a new Sudoku puzzle
        generateSudoku(); // Call method to generate Sudoku

        // Window close event handler
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                stopSolverThread(); // Ensure thread stops if window is closed
                System.exit(0); // Exit the application
            }
        });

        setVisible(true); // Make the frame visible
    }

    // Generate a valid Sudoku puzzle using backtracking
    private void generateSudoku() {
        stopSolverThread(); // Stop any previous solver
        solving = false;
        clearSudoku();      // Clear the internal grid
        fillGrid();         // Use backtracking to generate a complete valid Sudoku grid
        copySolution();     // Save the fully filled solution for validation later

        // Remove cells based on a difficulty (e.g., fixed number for now)
        makePuzzle(40); // Remove 40 cells (adjust for difficulty)

        // Update the GUI cells with the puzzle
        updateCellsInGUI();
        setButtonStates(true); // Enable buttons
        setAllCellsEditableBasedOnPuzzle(); // Set editability based on initial puzzle
    }

    // Clear the internal Sudoku grid
    private void clearSudoku() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                sudoku[i][j] = 0; // Reset each cell to 0
            }
        }
    }

    // Backtracking algorithm to fill the grid (for generation)
    private boolean fillGrid() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (sudoku[row][col] == 0) { // Check for empty cells
                    List<Integer> numbers = generateShuffledList(); // Get shuffled numbers 1-9

                    for (int num : numbers) {
                        if (isSafeForGeneration(row, col, num)) { // Check if it's safe to place the number
                            sudoku[row][col] = num; // Place the number

                            if (fillGrid()) { // Recur to fill the next cell
                                return true; // Successfully filled
                            }
                            sudoku[row][col] = 0; // Backtrack if not successful
                        }
                    }
                    return false; // Trigger backtracking
                }
            }
        }
        return true; // Completed filling the grid
    }

    // Generate a shuffled List of numbers 1-9
    private List<Integer> generateShuffledList() {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= SIZE; i++) {
            list.add(i);
        }
        Collections.shuffle(list);
        return list;
    }

    // Check if it's safe to place a number (used during initial generation)
    private boolean isSafeForGeneration(int row, int col, int num) {
        return !isInRowForGeneration(row, num) &&
               !isInColForGeneration(col, num) &&
               !isInBoxForGeneration(row - row % SUBGRID_SIZE, col - col % SUBGRID_SIZE, num);
    }

    // Check row during generation
    private boolean isInRowForGeneration(int row, int num) {
        for (int col = 0; col < SIZE; col++) {
            if (sudoku[row][col] == num) return true;
        }
        return false;
    }

    // Check column during generation
    private boolean isInColForGeneration(int col, int num) {
        for (int row = 0; row < SIZE; row++) {
            if (sudoku[row][col] == num) return true;
        }
        return false;
    }

    // Check 3x3 box during generation
    private boolean isInBoxForGeneration(int startRow, int startCol, int num) {
        for (int row = 0; row < SUBGRID_SIZE; row++) {
            for (int col = 0; col < SUBGRID_SIZE; col++) {
                if (sudoku[startRow + row][startCol + col] == num) return true;
            }
        }
        return false;
    }

    // Save the complete solution
    private void copySolution() {
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(sudoku[i], 0, solution[i], 0, SIZE);
        }
    }

    // Remove cells to create a Sudoku puzzle
    private void makePuzzle(int cellsToRemove) {
        Random rand = new Random();
        int removed = 0;
        // Ensure we have a unique solution - more complex, omitted for simplicity here
        // Basic removal:
        while (removed < cellsToRemove && removed < SIZE * SIZE) { // Added safety limit
            int row = rand.nextInt(SIZE);
            int col = rand.nextInt(SIZE);
            if (sudoku[row][col] != 0) {
                sudoku[row][col] = 0;
                removed++;
            }
        }
    }

    // Update GUI text fields with Sudoku values from the internal 'sudoku' array
    private void updateCellsInGUI() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                cells[row][col].setForeground(Color.BLACK); // Reset text color
                if (sudoku[row][col] != 0) {
                    cells[row][col].setText(String.valueOf(sudoku[row][col]));
                    // cells[row][col].setEditable(false); // Defer setting editability
                    cells[row][col].setBackground(FILLED_BG_COLOR); // Mark initial numbers
                    cells[row][col].setFont(new Font("SansSerif", Font.BOLD, 20));
                } else {
                    cells[row][col].setText("");
                    // cells[row][col].setEditable(true); // Defer setting editability
                    cells[row][col].setBackground(ORIGINAL_BG_COLOR); // Editable cells
                     cells[row][col].setFont(new Font("SansSerif", Font.PLAIN, 20)); // Maybe different font for user input
                }
            }
        }
         // Ensure focus doesn't get stuck on a non-editable field initially
        findFirstEditableCellAndFocus();
    }

     private void findFirstEditableCellAndFocus() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                // Check the internal model 'sudoku' to know if it *should* be editable
                if (sudoku[row][col] == 0) {
                    cells[row][col].requestFocus();
                    return;
                }
            }
        }
    }

    // --- Solver Visualization Logic ---

    // Thread for running the solver visualization
    class SolveThread extends Thread {
        @Override
        public void run() {
            solving = true;
            setButtonStates(false); // Disable buttons during solve

            // --- FIX: DO NOT set all cells non-editable here ---
            // setAllCellsEditable(false); // <--- REMOVED THIS LINE

            final boolean solved = solveSudokuVisual(); // Run the solver

            // Use EventQueue to update GUI after solving is done
            EventQueue.invokeLater(() -> {
                if (solved) {
                    System.out.println("Solved!");
                    // Optionally indicate success visually (e.g., final color already applied)
                    setAllCellsEditable(false); // NOW make cells non-editable AFTER successful solve
                } else {
                    System.out.println("Could not solve or was interrupted.");
                    // Optionally indicate failure visually, or reset colors?
                    // Reset colors if stopped mid-way?
                    if(!isBoardFullVisual()){ // if interrupted, reset background of non-filled cells
                         resetTryingCellBackgrounds();
                    }
                }

                solving = false;
                setButtonStates(true); // Re-enable buttons
                 // Ensure reset and end are always enabled after solve attempt
                resetButton.setEnabled(true);
                endButton.setEnabled(true);
                // Keep check/solution disabled if solved successfully
                if(solved) {
                    checkButton.setEnabled(false);
                    solutionButton.setEnabled(false);
                }

            });
            solverThread = null; // Thread finished
        }
    }

    // Starts the visualization
    private void startSolverVisualization() {
        if (solving) return; // Don't start if already running

        // Reset colors and ensure editability is correct *before* starting
        resetCellBackgrounds();
        setAllCellsEditableBasedOnPuzzle(); // Make sure only initially empty cells are editable

        solverThread = new SolveThread();
        solverThread.start();
    }

    // Stops the solver thread if running
    private void stopSolverThread() {
        if (solverThread != null && solverThread.isAlive()) {
            solving = false; // Signal the thread to stop early
            solverThread.interrupt(); // Interrupt the sleep
            try {
                solverThread.join(500); // Wait briefly for it to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
        }
        solverThread = null;
        solving = false;
        // Re-enable buttons if solver is stopped externally
        // Use invokeLater as this might be called from different threads
        EventQueue.invokeLater(() -> setButtonStates(true));
    }


    // Recursive backtracking solver with visualization
    private boolean solveSudokuVisual() {
         if (!solving) return false; // Check if stop was requested

        int[] find = findEmptyCellVisual();
        if (find == null) {
            return true; // No empty cells, solved!
        }
        int row = find[0];
        int col = find[1];

        // Ensure the found cell is indeed editable (safety check)
        if(!cells[row][col].isEditable()) {
             System.err.println("Error: findEmptyCellVisual returned non-editable cell?");
             return solveSudokuVisual(); // Skip and try next empty cell
        }

        for (int num = 1; num <= SIZE; num++) {
             if (!solving) return false; // Check if stop was requested

            // Check if the number is safe in the current visual grid state
            if (isSafeVisual(row, col, num)) {
                final int finalNum = num;
                final int finalRow = row;
                final int finalCol = col;

                EventQueue.invokeLater(() -> {
                    cells[finalRow][finalCol].setText(String.valueOf(finalNum));
                    cells[finalRow][finalCol].setBackground(SOLVING_BG_COLOR); // Highlight trying
                    cells[finalRow][finalCol].setForeground(Color.BLUE); // Color for trying
                });

                pauseSolver(getDelay());

                 if (!solving) { // Check again after pause, cleanup needed
                     EventQueue.invokeLater(() -> {
                         // Only clear if it's still the number we placed (might have been changed by fast interrupt)
                         if (cells[finalRow][finalCol].getText().equals(String.valueOf(finalNum)) && cells[finalRow][finalCol].isEditable()) {
                            cells[finalRow][finalCol].setText("");
                            cells[finalRow][finalCol].setBackground(ORIGINAL_BG_COLOR);
                            cells[finalRow][finalCol].setForeground(Color.BLACK);
                         }
                     });
                    return false;
                 }

                if (solveSudokuVisual()) {
                     // Success! Mark this cell as part of the final solution
                     EventQueue.invokeLater(() -> {
                         cells[finalRow][finalCol].setBackground(FINAL_SOLVE_COLOR); // Mark as correct
                         cells[finalRow][finalCol].setForeground(Color.BLACK); // Final text color
                     });
                    return true; // Found solution path
                }

                // Backtrack: If recursion failed, undo the choice visually
                 if (!solving) return false; // Check if stop was requested

                 // Make sure we are backtracking from the number we placed
                 final String currentTextInCell = cells[row][col].getText(); // Read value *before* invokeLater

                 EventQueue.invokeLater(() -> {
                     // Only clear if it's still the number we placed during this step
                     if (cells[finalRow][finalCol].isEditable() && currentTextInCell.equals(String.valueOf(finalNum))) {
                         cells[finalRow][finalCol].setText(""); // Clear the cell
                         cells[finalRow][finalCol].setBackground(BACKTRACK_BG_COLOR); // Indicate backtracking
                         cells[finalRow][finalCol].setForeground(Color.RED); // Backtrack text color (optional)
                     }
                 });

                pauseSolver(getDelay() / 2); // Shorter pause for backtracking visibility

                 EventQueue.invokeLater(() -> {
                     // Only reset color if it's still marked as backtracking
                     if (cells[finalRow][finalCol].isEditable() && cells[finalRow][finalCol].getBackground() == BACKTRACK_BG_COLOR) {
                         cells[finalRow][finalCol].setBackground(ORIGINAL_BG_COLOR); // Reset color
                         cells[finalRow][finalCol].setForeground(Color.BLACK);
                     }
                 });
            } // end if isSafeVisual
        } // end for num

        return false; // No number worked for this cell, trigger backtrack from caller
    }

    // Find the next empty cell in the GUI grid intended for user input/solving
    private int[] findEmptyCellVisual() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                // Find cell that was originally empty (check internal 'sudoku' model)
                // AND is currently empty in the GUI
                if (sudoku[row][col] == 0 && cells[row][col].getText().trim().isEmpty()) {
                     // Double check editability just in case state is inconsistent
                     if (cells[row][col].isEditable()){
                        return new int[]{row, col};
                     } else {
                         // This case indicates a potential state management issue, but we'll log it and continue searching
                         System.err.println("Warning: Cell " + row + "," + col + " should be editable but isn't during solve search.");
                     }
                }
            }
        }
        return null; // No empty cells found
    }


    // Check safety based on the current state of the GUI TextFields
    private boolean isSafeVisual(int row, int col, int num) {
        String numStr = String.valueOf(num);
        // Check row
        for (int c = 0; c < SIZE; c++) {
            if (c != col && cells[row][c].getText().equals(numStr)) { // Don't check against self
                return false;
            }
        }
        // Check column
        for (int r = 0; r < SIZE; r++) {
            if (r != row && cells[r][col].getText().equals(numStr)) { // Don't check against self
                return false;
            }
        }
        // Check 3x3 box
        int startRow = row - row % SUBGRID_SIZE;
        int startCol = col - col % SUBGRID_SIZE;
        for (int r = 0; r < SUBGRID_SIZE; r++) {
            for (int c = 0; c < SUBGRID_SIZE; c++) {
                int checkRow = startRow + r;
                int checkCol = startCol + c;
                if (checkRow != row || checkCol != col) { // Don't check against self
                    if (cells[checkRow][checkCol].getText().equals(numStr)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // Pause the solver thread
    private void pauseSolver(int milliseconds) {
        if (milliseconds <= 0) return; // Don't sleep if delay is zero or less
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            // Thread interrupted, likely by stopSolverThread() or window close
            solving = false; // Ensure the flag is set to stop processing
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }
    }

    // Get delay from the speed TextField
    private int getDelay() {
        try {
            int delay = Integer.parseInt(speedField.getText().trim());
            return Math.max(0, delay); // Allow zero delay, minimum is 0
        } catch (NumberFormatException e) {
            return 100; // Default delay if input is invalid
        }
    }

     // Reset background colors of all cells based on initial puzzle state
    private void resetCellBackgrounds() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                 if(sudoku[row][col] == 0){ // Originally empty cell
                    cells[row][col].setBackground(ORIGINAL_BG_COLOR);
                 } else { // Originally filled cell
                     cells[row][col].setBackground(FILLED_BG_COLOR);
                 }
                 cells[row][col].setForeground(Color.BLACK); // Reset text color too
            }
        }
    }

     // Reset background for cells that might be left in a 'trying' or 'backtrack' state if interrupted
     private void resetTryingCellBackgrounds() {
         for (int row = 0; row < SIZE; row++) {
             for (int col = 0; col < SIZE; col++) {
                 Color currentBg = cells[row][col].getBackground();
                 if (currentBg == SOLVING_BG_COLOR || currentBg == BACKTRACK_BG_COLOR) {
                     if (sudoku[row][col] == 0) { // Should be an originally empty cell
                         cells[row][col].setBackground(ORIGINAL_BG_COLOR);
                         cells[row][col].setForeground(Color.BLACK);
                     } else { // Should be a pre-filled cell (this case is less likely)
                         cells[row][col].setBackground(FILLED_BG_COLOR);
                         cells[row][col].setForeground(Color.BLACK);
                     }
                 }
             }
         }
     }


     // Enable/disable cells' editability based on the original puzzle stored in sudoku[][]
    private void setAllCellsEditableBasedOnPuzzle() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                cells[row][col].setEditable(sudoku[row][col] == 0);
            }
        }
    }

     // Make ALL cells editable or not (used after solving/checking)
    private void setAllCellsEditable(boolean editable) {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                 cells[row][col].setEditable(editable);
            }
        }
    }

    // Check if the board is visually full
    private boolean isBoardFullVisual() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (cells[row][col].getText().trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    // --- End Solver Visualization Logic ---


    // Check user's solution against the stored complete solution
    private boolean checkUserSolution() {
        boolean allCorrect = true;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                 String text = cells[row][col].getText().trim();
                 if (text.isEmpty()) {
                     // Mark originally empty cells that are still empty as incorrect (incomplete)
                     if (sudoku[row][col] == 0) {
                         // No need to color empty cells red, just note it's not correct
                         allCorrect = false;
                     }
                     // If it wasn't originally empty but is now, that's also wrong (shouldn't happen)
                     else {
                         allCorrect = false;
                     }
                     continue; // Move to next cell if empty
                 }

                 try {
                     int userValue = Integer.parseInt(text);
                     if (userValue != solution[row][col]) {
                         // If the value is wrong, mark it red (only if it was user-editable)
                         if (sudoku[row][col] == 0) { // Check if it was an originally empty cell
                            cells[row][col].setForeground(Color.RED);
                         }
                         // If a pre-filled cell was somehow changed to wrong value (shouldn't happen), mark error
                         else {
                             cells[row][col].setForeground(Color.RED); // Or handle differently
                         }
                         allCorrect = false; // Mark as incorrect
                     } else {
                         // If correct, ensure text color is black (or green if preferred for correct user input)
                         cells[row][col].setForeground(Color.BLACK);
                     }
                 } catch (NumberFormatException e) {
                      // If non-numeric input, mark red (only if user-editable)
                      if (sudoku[row][col] == 0) {
                         cells[row][col].setForeground(Color.RED);
                      }
                      allCorrect = false; // Non-numeric is incorrect
                 }
            }
        }
        return allCorrect; // Return overall correctness
    }

    // Thread to check user's solution (keeps UI responsive)
    class CheckThread extends Thread {
        public void run() {
            // Reset any previous error highlights before checking
            EventQueue.invokeLater(() -> {
                 for (int row = 0; row < SIZE; row++) {
                     for (int col = 0; col < SIZE; col++) {
                          // Only reset color if it was an editable cell initially
                          if (sudoku[row][col] == 0) {
                             cells[row][col].setForeground(Color.BLACK);
                          }
                     }
                 }
            });


            // Give the UI a moment to reset colors before checking
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

            final boolean isCorrect = checkUserSolution(); // Check solution

            // Use EventQueue to show dialog and update UI from this thread
            EventQueue.invokeLater(() -> {
                if (isCorrect) {
                    showDialog("Correct Solution!"); // Show success dialog
                    setAllCellsEditable(false); // Lock correct board
                    setButtonStates(false); // Disable Check/Solve after correct
                    resetButton.setEnabled(true); // Keep Reset enabled
                    endButton.setEnabled(true); // Keep End enabled
                } else {
                    showDialog("Incorrect or Incomplete Solution! Check red numbers."); // Show failure dialog
                }
            });
        }
    }

    // Display a simple dialog message
    private void showDialog(String message) {
        Dialog d = new Dialog(this, "Result", true); // Create dialog
        d.setLayout(new FlowLayout()); // Set layout
        d.add(new Label(message)); // Add message label
        Button b = new Button("OK"); // Create OK button
        b.addActionListener(e -> d.setVisible(false)); // Close dialog
        d.add(b); // Add button to dialog
        d.setSize(350, 100); // Set dialog size
        d.setLocationRelativeTo(this); // Center dialog
        d.setVisible(true); // Make dialog visible
    }

    // Enable or disable buttons (useful during solving)
    private void setButtonStates(boolean enabled) {
         checkButton.setEnabled(enabled);
         resetButton.setEnabled(enabled);
         solutionButton.setEnabled(enabled);
         endButton.setEnabled(true); // Keep End always enabled
         speedField.setEnabled(enabled);
    }

    // Action listener for buttons
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (source == checkButton) {
            stopSolverThread(); // Stop solver if running
            CheckThread checkThread = new CheckThread(); // Create thread to check solution
            checkThread.start(); // Start thread
        } else if (source == resetButton) {
            stopSolverThread(); // Stop solver if running
            generateSudoku(); // Generate and display a new puzzle
        } else if (source == solutionButton) {
            stopSolverThread(); // Stop any previous solve attempt
            startSolverVisualization(); // Start the step-by-step solution
        } else if (source == endButton) {
            stopSolverThread(); // Ensure thread stops
            dispose(); // Close the Sudoku window
            System.exit(0); // Ensure application exits cleanly
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(Sudoku::new); // Ensure GUI creation is on the EDT
    }
}
package hai913i.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import hai913i.tp2.processors.Processor;

public abstract class AbstractMain {
	public static String TEST_PROJECT_PATH;
	public static final String QUIT = "0";
	
	protected static void setTestProjectPath(BufferedReader inputReader) 
			throws IOException {
		
		System.out.println("Please provide the path to a java project folder: ");
		String userInput = inputReader.readLine();
		verifyTestProjectPath(inputReader, userInput);
	}
	
	protected static void verifyTestProjectPath(BufferedReader inputReader, String userInput) throws IOException {
		while (!isJavaProject(userInput)) {
			System.err.println("Error: "+userInput+
					" either doesn't exist or isn't a java project folder. "
					+ "Please try again: ");
			userInput = inputReader.readLine();
		}
		
		TEST_PROJECT_PATH = userInput;
	}

    protected static boolean isJavaProject(String projectPath) {
        File root = new File(projectPath);
        if (!root.exists() || !root.isDirectory()) return false;

        // Accept: src/, src/main/java/, ou src/java/
        return new File(root, "src").exists()
                || new File(root, "src/main/java").exists()
                || new File(root, "src/java").exists();
    }
    
}

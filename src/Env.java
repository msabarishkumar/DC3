
public class Env {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	public Env(){
		String directory = System.getProperty("user.dir");
		if (directory.substring(directory.length() - 4).equals("/bin")){
			directory = directory.substring(0,directory.length() - 4);
		}
		
		System.setProperty("LOG_FOLDER",directory + "/logs");
		System.setProperty("CONFIG_NAME",directory + "/config");
		
		
	}

	private void updateConfig(){
		updateConfig();
	}
}

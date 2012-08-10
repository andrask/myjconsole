package andrask.sun.tools.jconsole;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import com.sun.istack.internal.logging.Logger;

public class Settings {
	public static Properties getProperties() {
		try {
			Properties properties = new Properties();
			properties.load(new FileReader(getPropertiesFile()));
			return properties;
		} catch (Exception e) {
			return new Properties();
		}
	}

	public static void saveProperties(Properties properties) {
		try {
			properties.store(new FileOutputStream(getPropertiesFile()), "My JConsole properties");
		} catch (Exception e) {
			Logger.getLogger(Settings.class).warning("Could not save preferences", e);
		}
	}
	
	private static File getPropertiesFile() {
		File file = new File(getPropertiesFilename());
		try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return file;
	}

	private static String getPropertiesFilename() {
		return System.getProperty("user.home") + File.separator + ".myjconsole.properties";
	}
	
	public static String getProperty(String key, String defValue) {
		final Properties properties = getProperties();
		String propertyValue = properties.getProperty(key);
		if (propertyValue == null) {
			properties.put(key, defValue);
			saveProperties(properties);
			propertyValue = defValue;
		}
		return propertyValue;
	}
	
	public static Properties setProperty(String key, String value) {
		final Properties properties = getProperties();
		properties.setProperty(key, value);
		saveProperties(properties);
		return properties;
	}

	public static int getInt(String key, int defValue) {
		return new Integer(getProperty(key, new Integer(defValue).toString()));
	}
	
	public static void setInt(String key, int value) {
		setProperty(key, new Integer(value).toString());
	}

	public static final String KEY_MBEANS_VIEW_WIDTH = "myjconsole.mbeansview.width"; 
}

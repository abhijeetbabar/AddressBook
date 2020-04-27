 /*
  * Title:          com.AddressBook.Database
  * Authors:        Miles Maloney, Caden Keese, Kanan Boubion, Maxon Crumb, Scott Spinali
  * Last Modified:  4/22/20
  * Description:
  * */
 package com.AddressBook.Database;

 import com.AddressBook.AddressEntry;
 import org.jetbrains.annotations.NotNull;
 import org.jetbrains.annotations.Nullable;

 import java.io.IOException;
 import java.io.UnsupportedEncodingException;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.nio.file.Paths;
 import java.security.GeneralSecurityException;
 import java.util.HashMap;
 import java.util.Map;

 import static java.nio.charset.StandardCharsets.US_ASCII;
 import static java.nio.file.StandardOpenOption.CREATE;
 import static java.nio.file.StandardOpenOption.WRITE;


 public class AddressDatabase {
     /**
      * this interface is used to provide a lambda to encrypt a string when calling {@link #set}
      */
     public interface Encrypter {
         String encrypt(String plainText) throws GeneralSecurityException, UnsupportedEncodingException;
     }

     /**
      * this interface is used to provide a lambda to decrypt a string when calling {@link #set}, {@link #get}, {@link #exists} and {@link #isFull}
      */
     public interface Decrypter {
         String decrypt(String encrypted) throws GeneralSecurityException, UnsupportedEncodingException;
     }
     /**
      * The maximum number of records a single user can have
      */
     private static final int MAX_RECORDS = 256;
     /**
      * the folder where all record files are stored
      */
     private static final String FOLDER_NAME = ".addresses";
     /**
      * the prefix of the file is combined with the userId to create the file name where the record is stored
      */
     private static final String FILE_PREFIX = "u_";
     /**
      * this is used to store the current userId to check if the user has changed
      */
     private String currentUserId;
     /**
      * this is used to store the records in memory
      */
     private Map<String, AddressEntry> map;

     /**
      * Read the user record file
      *
      * @param userId the user of the file to read
      * @return the encrypted contents of the user record
      * @throws IOException if it fails to read the file
      */
     private @Nullable String readFile(String userId) throws IOException {
         Path path = Paths.get(FOLDER_NAME, FILE_PREFIX + userId);
         if (!Files.exists(path)) {
             return null;
         } else {
             try {
                 return Files.readString(path, US_ASCII);
             } catch (IOException e) {
                 throw new IOException("Database exists and Failed to Read!");
             }
         }
     }

     /**
      * @param userId the user of the file to write
      * @param data   the encrypted data to write to the file
      * @throws IOException if if fails to write the file
      */
     private void writeFile(String userId, String data) throws IOException {
         Path path = Paths.get(FOLDER_NAME, FILE_PREFIX + userId);
         try {
             Files.writeString(path, data, US_ASCII, CREATE, WRITE);
         } catch (IOException e) {
             throw new IOException("User Database Failed to Write!");
         }
     }

     /**
      * @param userId    the user of the file to access
      * @param decrypter function to decrypt the data
      * @return map of the data loaded from the file
      * @throws IOException if fails to read file or misformatted
      */
     private @NotNull Map<String, AddressEntry> getMapFromFile(String userId, Decrypter decrypter) throws IOException, GeneralSecurityException {
         @Nullable String encypted = readFile(userId);
         if (encypted == null) {
             return new HashMap<>();
         } else {
             String data = decrypter.decrypt(encypted);
             return getMapFromString(data);
         }

     }

     /**
      * get map from csv string
      *
      * @param data the database data in csv format
      * @return a map of the data passed in
      * @throws IOException if there are duplicates
      */
     private @NotNull Map<String, AddressEntry> getMapFromString(@NotNull String data) throws IOException {
         String[] lines = data.split("\n");
         Map<String, AddressEntry> m = new HashMap<>(lines.length);
         for (String line : lines) {
             AddressEntry ae = new AddressEntry(line);
             if (m.containsKey(ae.recordId)) {
                 throw new IOException("Duplicate Records when loading Address Database!");
             }
             m.put(ae.recordId, ae);
         }
         return m;
     }

     /**
      * @param userId    the user of the file to access
      * @param encrypter function to encrypt data
      * @param m         the map of data to store in the file
      * @throws IOException if fails to write
      */
     private void setFileFromMap(String userId, @NotNull Encrypter encrypter, @NotNull Map<String, AddressEntry> m) throws IOException, GeneralSecurityException {
         StringBuilder sb = new StringBuilder();
         m.forEach((k, v) -> {
             sb.append(v.toString()).append("\n");
         });
         sb.deleteCharAt(sb.lastIndexOf("\n"));
         writeFile(userId, encrypter.encrypt(sb.toString()));
     }

     /**
      * @param userId    user associate with the data to load
      * @param decrypter function to decrypt the data
      * @throws IOException if fails to read db file
      */
     private void instantiateMapIfNeeded(String userId, Decrypter decrypter) throws IOException, GeneralSecurityException {
         if (map == null || currentUserId == null || !currentUserId.equals(userId)) {
             map = getMapFromFile(userId, decrypter);
             currentUserId = userId;
         }
     }

     /**
      * Get a record
      *
      * @param userId    the user whose record to get
      * @param recordId  the id of the record to get
      * @param decrypter function to decrypt records
      * @return the record with the passed id
      * @throws IOException if database fails to load from file
      */
     public @Nullable AddressEntry get(String userId, String recordId, Decrypter decrypter) throws IOException, GeneralSecurityException {
         instantiateMapIfNeeded(userId, decrypter);
         return map.get(recordId);
     }

     /**
      * @param userId    user associated with record to remove
      * @param recordId  the record to remove
      * @param decrypter function to decrypt data
      * @param encrypter function to encrypt data
      * @throws IOException              on failure to load or store database file
      * @throws GeneralSecurityException if encryption fails
      */
     public void delete(String userId, String recordId, Decrypter decrypter, Encrypter encrypter) throws IOException, GeneralSecurityException {
         instantiateMapIfNeeded(userId, decrypter);
         if (!map.containsKey(recordId)) {
             throw new IOException("Record Not Found");
         } else {
             map.remove(recordId);
             setFileFromMap(userId, encrypter, map);
         }
     }

     /**
      * Set the value of a record
      *
      * @param userId    the user whose record to set
      * @param entry     the entry to set
      * @param decrypter function to decrypt records
      * @param encrypter function to encrypt records
      * @throws IOException if database fails to load from file or save to file
      */
     public void set(String userId, AddressEntry entry, Decrypter decrypter, Encrypter encrypter) throws IOException, GeneralSecurityException {
         instantiateMapIfNeeded(userId, decrypter);
         if (!isFull(userId, decrypter) || map.containsKey(entry.recordId)) {
             map.put(entry.recordId, entry);
             setFileFromMap(userId, encrypter, map);
         } else {
             throw new IOException("Number of records exceeds maximum");
         }
     }

     /**
      * Check if a record exists
      *
      * @param userId    the user whose records to check
      * @param decrypter function to decrypt records
      * @return if the record exists
      * @throws IOException if database fails to load from file
      */
     public boolean exists(String userId, Decrypter decrypter) throws IOException, GeneralSecurityException {
         instantiateMapIfNeeded(userId, decrypter);
         return map.containsKey(userId);
     }

     /**
      * Check if database is full
      *
      * @param userId    user of the file to access
      * @param decrypter function to decrypt data
      * @return if the database is full
      * @throws IOException if fails to read db file
      */
     public boolean isFull(String userId, Decrypter decrypter) throws IOException, GeneralSecurityException {
         instantiateMapIfNeeded(userId, decrypter);
         return !(map.size() < MAX_RECORDS);
     }

     /**
      * Export database as CSV string
      *
      * @param userId    user of data to access
      * @param decrypter function to decrypt data
      * @return CSV string of user's database
      * @throws IOException if failed to read db file
      */
     public String exportDB(String userId, Decrypter decrypter) throws IOException, GeneralSecurityException {
         String raw = readFile(userId);
         return decrypter.decrypt(raw);
     }

     /**
      * Import CSV string into database
      *
      * @param userId    user of data to access
      * @param encrypter function to encrypt data
      * @param data      CSV data to add
      * @throws IOException if fails to read or write db file
      */
     public void importDB(String userId, Decrypter decrypter, Encrypter encrypter, @NotNull String data) throws IOException, GeneralSecurityException {
         instantiateMapIfNeeded(userId, decrypter);
         @NotNull Map<String, AddressEntry> m = getMapFromString(data);
         for (Map.Entry<String, AddressEntry> entry : m.entrySet()) {
             if (map.containsKey(entry.getKey())) {
                 throw new IOException("Duplicate recordID");
             } else {
                 map.put(entry.getKey(), entry.getValue());
             }
         }
     }

     public static AddressDatabase getInstance() {
         if (addressDatabase == null) {
             addressDatabase = new AddressDatabase();
         }
         return addressDatabase;
     }

     private static AddressDatabase addressDatabase;

     private AddressDatabase() {
     }
 }


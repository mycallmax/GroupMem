import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.io.BufferedReader;

public class tf
{
  public static void main(String[] args) throws IOException
  {
    if (args.length == 0){
      System.out.println("Need at least one input file");
      return;
    }

    String prefix = args[0];
    String job_id = args[1];
    for (int i = 2; i < args.length; i++)
    {
      HashMap<String, Integer> keymap = new HashMap<String, Integer>();
      File file = new File(args[i]);
      BufferedReader br = new BufferedReader(new FileReader(file));

      int currentchar;
      String key = "";
      int wordnum = 0;
      int charnum = 0;
      boolean emptyfile = true;
      char temp [] = new char[2048];

      while ((currentchar = br.read()) != -1) {
        emptyfile = false;
        //System.out.println(currentchar);
        if (((currentchar >= 65) && (currentchar <= 90))||((currentchar >= 97) && (currentchar <= 122))||((currentchar >= 48) && (currentchar <= 57))){
          temp[charnum++] = (char) currentchar;
        }
        else
        {
          // if the first of word is symbol, ignore the empty word
          if (charnum == 0)
            continue;

          wordnum++; 
          key = key.concat(new String(temp, 0, charnum));
          if (keymap.containsKey(key)){
            int value = (Integer) keymap.get(key) + 1;
            keymap.put(key, value);
          }
          else
            keymap.put(key,1);
          key = "";
          charnum = 0;
        }
      }
      br.close();
      // add the last word
      if ((emptyfile == false) && (charnum != 0)){
        key = key.concat(new String(temp));
        if (keymap.containsKey(key)){
          int value = (Integer) keymap.get(key) + 1;
          keymap.put(key, value);
        }
        else
          keymap.put(key,1);
        wordnum++;
      }

      //System.out.println(keymap.values());
      ToFile(prefix, job_id, wordnum, keymap, args[i]);
    }

  }

  static void ToFile(String prefix, String job_id, int wordtotal, HashMap<String, Integer> keyMap,String doc) throws IOException {
    Set<String> keys = keyMap.keySet();
    for(String key : keys) {
      String filename = prefix + "_" + key + "_" + job_id;
      int wordc = (Integer) keyMap.get(key);
      FileWriter buf_writer = new FileWriter(filename);
      buf_writer.write(Double.toString((double)wordc/(double)wordtotal)+"\t" + doc + "\n");
      buf_writer.close();
    }
  }
}
package whatsmysurroundings.client.WMSCommands;

import java.util.ArrayList;
import java.util.List;

public class WMSCommands {
   private static List<String> WMSCommands = new ArrayList<>();

   public static void addCommandString(String commandString){
      WMSCommands.add(commandString);
   }

   public static List<String> getWMSCommands(){
      return WMSCommands;
   }

   public static void init() {
      //TestCommand.register();
      wmsblockCommand.register();
      wmshelpCommand.register();
   }
}

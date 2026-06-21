package whatsmysurroundings.client.Commands;

import java.util.ArrayList;
import java.util.List;

public class CustomedCommands {
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

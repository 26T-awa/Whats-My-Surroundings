package whatsmysurroundings.client.Commands;

import java.util.ArrayList;
import java.util.List;

import whatsmysurroundings.client.Commands.wmsblockCommand;
import whatsmysurroundings.client.Commands.wmshelpCommand;

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
      //wmsentityCommand.register();
   }
}

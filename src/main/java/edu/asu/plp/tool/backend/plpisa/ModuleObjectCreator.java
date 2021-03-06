package edu.asu.plp.tool.backend.plpisa;

import java.util.function.Function;


import edu.asu.plp.tool.backend.plpisa.assembler2.PLPAssembler;
import edu.asu.plp.tool.backend.plpisa.sim.PLPSimulator;

public class ModuleObjectCreator 
{
	public static final String ISA_NAME = "plp";
	public static PLPISAModule getModule()
	{
		//PLPISAModule isaMod = null;
		
		Function<String, Boolean> supportsProjectType;
		supportsProjectType = (type) -> type.toLowerCase().startsWith(ModuleObjectCreator.ISA_NAME);
		PLPAssembler assembler = new PLPAssembler();
		assembler.startListening();
		PLPSimulator simulator = new PLPSimulator();
		simulator.startListening();
		return new PLPISAModule(assembler, simulator, supportsProjectType);
		
		//return isaMod;
	}

}

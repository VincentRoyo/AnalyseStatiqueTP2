package hai913i.tp2.spoon.processors;

import hai913i.tp2.processors.Processor;
import hai913i.tp2.spoon.parsers.SpoonParser;

public class SpoonProcessor extends Processor<SpoonParser> {

	public SpoonProcessor(String projectPath) {
		super(projectPath);
	}

	
	public void setParser(String projectPath) {
		parser = new SpoonParser(projectPath);
	}
	
	public void setParser(SpoonParser parser) {
		this.parser = parser;
	}

}

package hai913i.tp2.spoon.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.AbstractFilter;

public class FieldsWithGetterFilter extends AbstractFilter<CtField> {
	
	/* ATTRIBUTES */
	private CtClass target;
	
	private List<CtMethod> getters = new ArrayList<>();
	
	/* CONSTRUCTOR */
	public FieldsWithGetterFilter(CtClass target) {
		this.target = target;
	}
	
	/* METHODS */
	public CtClass getTarget() {
		return target;
	}
	
	public void setTarget(CtClass target) {
		this.target = target;
	}
	
	public List<CtMethod> getGetters() {
		return getters;
	}
	
	
	public boolean matches(CtField field) {
		boolean matches = false;
		String fieldName = field.getSimpleName();
		Set<CtMethod> methods = target.getMethods();
		
		for (CtMethod method: methods) {
			String getterFieldName = getGetterFieldName(method.getSimpleName());
			if (!getterFieldName.isEmpty()) {
				matches = matches || fieldName.equals(getterFieldName);
				
				if (matches) {
					getters.add(method);
					break;
				}
			}
		}
		
		return matches;
	}
	
	public static String getGetterFieldName(String getterMethodName) {
		String fieldName = "";
		String getterRegex = "get(([A-Z][a-z0-9]*)+)";
		Pattern getterPattern = Pattern.compile(getterRegex);
		Matcher getterMatcher = getterPattern.matcher(getterMethodName);
		
		if (getterMatcher.matches())
			fieldName = decapitalize(getterMatcher.group(1));
		
		return fieldName;
	}
	
	public static String decapitalize(String string) {
		return string.substring(0,1).toLowerCase() + 
				string.substring(1, string.length());
	}
}

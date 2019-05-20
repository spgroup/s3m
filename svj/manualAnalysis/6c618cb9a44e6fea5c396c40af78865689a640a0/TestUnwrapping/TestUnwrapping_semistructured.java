package com.fasterxml.jackson.module.jsonSchema; 

import com.fasterxml.jackson.annotation.JsonUnwrapped; 
import com.fasterxml.jackson.databind.ObjectMapper; 
 
 
 

public  class  TestUnwrapping  extends SchemaTestBase {
	
    static  class  UnwrappingRoot {
		
        public int age;
		

        @JsonUnwrapped(prefix="name.")
        public Name name;

	}
	

    static  class  Name {
		
        public String first, last;

	}
	

    /*
    /**********************************************************
    /* Unit tests, success
    /**********************************************************
     */
    
    private final ObjectMapper MAPPER = objectMapper();
	

    public void testUnwrapping()  throws Exception
    {
        JsonSchemaGenerator generator = new JsonSchemaGenerator(MAPPER);
        JsonSchema schema = generator.generateSchema(UnwrappingRoot.class);

        String json = MAPPER.writeValueAsString(schema).replace('"', '\'');
        
//System.err.println("JSON -> "+MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_7426355078610794350.java
        String EXP = "{'type':'object'," +
                     "'id':'urn:jsonschema:com:fasterxml:jackson:module:jsonSchema:TestUnwrapping:UnwrappingRoot'," +
                     "'properties':{'age':{'type':'integer'},'name.first':{'type':'string'},'name.last':{'type':'string'}}}";

System.err.println("EXP: "+EXP);
System.err.println("ACT: "+json);
=======
        String EXP = "{'type':'object','properties':{"
        +"'name.last':{'type':'string'},'name.first':{'type':'string'},"
        +"'age':{'type':'number','type':'integer'}}}";
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_7937202215282710758.java
        
        assertEquals(EXP, json);
    }

}


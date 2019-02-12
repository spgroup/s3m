package com.tinkerpop.blueprints.util.wrappers.batch.cache; 

import com.tinkerpop.blueprints.Vertex; 

 
 

/**
 * (c) Matthias Broecheler (me@matthiasb.com)
 */

public  class  StringIDVertexCache  extends AbstractIDVertexCache   {
	

    
	

    
	
    private final StringCompression compression;
	

    public StringIDVertexCache(final StringCompression compression) {
        super();
        if (compression == null) throw new IllegalArgumentException("Compression expected.");
        this.compression = compression;
    }
	

    public StringIDVertexCache() {
        this(StringCompression.NO_COMPRESSION);
    }
	

    @Override
    public Object getEntry(Object externalId) {
        return super.getEntry(compression.compress(externalId.toString()));
    }
	

    <<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_9214745412484072745.java
@Override
    public void set(Vertex vertex, Object externalId) {
        setId(vertex, externalId);
    }=======
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_1962704392359278098.java

	

    @Override
    public void setId(Object vertexId, Object externalId) {
        super.setId(vertexId, compression.compress(externalId.toString()));
    }
	

    @Override
    public boolean contains(Object externalId) {
        return super.contains(compression.compress(externalId.toString()));
    }
	

    

}


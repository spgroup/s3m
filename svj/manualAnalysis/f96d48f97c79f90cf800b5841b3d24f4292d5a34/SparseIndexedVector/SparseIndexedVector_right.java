package mikera.vectorz.impl;

import mikera.indexz.Index;
import mikera.matrixx.AMatrix;
import mikera.matrixx.impl.AVectorMatrix;
import mikera.matrixx.impl.IFastColumns;
import mikera.matrixx.impl.SparseRowMatrix;
import mikera.vectorz.AVector;
import mikera.vectorz.Op;
import mikera.vectorz.Op2;
import mikera.vectorz.Vector;
import mikera.vectorz.util.DoubleArrays;
import mikera.vectorz.util.IntArrays;
import mikera.vectorz.util.VectorzException;

/**
 * Indexed sparse vector. Efficient for mutable, mostly sparse vectors. 
 * 
 * Maintains a indexed array of elements which may be non-zero. These values *can* also be zero.
 * 
 * WARNING: individual updates of non-indexed elements are O(n) in the number of non-sparse elements. You should not normally
 * perform element-wise mutation on a SparseIndexedVector if performance is a concern
 * 
 * Index must be distinct and sorted.
 * 
 * @author Mike
 *
 */
public class SparseIndexedVector extends ASparseIndexedVector {
	private static final long serialVersionUID = 750093598603613879L;

	/**
	 * double[] array containing the non-sparse values for this indexed vector
	 * index contains the indexes of the vector that are non-sparse
	 * Both can be modified, enabling arbitrary mutation of this vector
	 */
	private double[] data;
	private Index index;
	
	private SparseIndexedVector(int length, Index index) {
		this(length,index,new double[index.length()]);
	}
	
	private SparseIndexedVector(int length, Index index, double[] data) {
		super(length);
		this.index=index;
		this.data=data;
	}
	
	private SparseIndexedVector(int length, Index index, AVector source) {
		this(length,index,source.toDoubleArray());
	}
	
	/**
	 * Creates a SparseIndexedVector with the specified index and data values.
	 * Performs no checking - Index must be distinct and sorted.
	 */
	public static SparseIndexedVector wrap(int length, Index index, double[] data) {
		assert(index.length()==data.length);
		assert(index.isDistinctSorted());
		return new SparseIndexedVector(length, index,data);
	}
	
	/**
	 * Creates a SparseIndexedVector with the specified index and data values.
	 * Performs no checking - Index must be distinct and sorted.
	 */
	public static SparseIndexedVector wrap(int length, int[] indices, double[] data) {
		Index index=Index.wrap(indices);
		assert(index.length()==data.length);
		assert(index.isDistinctSorted());
		return new SparseIndexedVector(length, index,data);
	}
	
	/**
	 * Creates a SparseIndexedVector using the given sorted Index to identify the indexes of non-zero values,
	 * and a double[] array to specify all the non-zero element values
	 */
	public static SparseIndexedVector create(int length, Index index, double[] data) {
		if (!index.isDistinctSorted()) {
			throw new VectorzException("Index must be sorted and distinct");
		}
		if (!(index.length()==data.length)) {
			throw new VectorzException("Length of index: mismatch woth data");			
		}
		return new SparseIndexedVector(length, index.clone(),DoubleArrays.copyOf(data));
	}
	
	/**
	 * Creates a sparse indexed vector using the specified indexed values from the source vector
	 * 
	 * All non-indexed vales will be zero.
	 * 
	 * @param v
	 * @param ixs
	 * @return
	 */
	public static AVector createWithIndices(AVector v, int[] ixs) {
		int length=v.length();
		int n=ixs.length;
		double[] data = new double[n];
		v.getElements(data,0,ixs);
		return wrap(length,ixs,data);
	}
	
	public static SparseIndexedVector createLength(int length) {
		return new SparseIndexedVector(length, Index.EMPTY,DoubleArrays.EMPTY);
	}
	
	/**
	 * Creates a SparseIndexedVector using the given sorted Index to identify the indexes of non-zero values,
	 * and a dense vector to specify all the non-zero element values
	 */
	public static SparseIndexedVector create(int length, Index index, AVector data) {
		SparseIndexedVector sv= create(length, index, new double[index.length()]);
		data.getElements(sv.data, 0);
		return sv;
	}
	
	/** 
	 * Creates a SparseIndexedVector from the given vector, ignoring the zeros in the source.
	 * 
	 */
	public static SparseIndexedVector create(AVector source) {
		if (source instanceof ASparseVector) return create((ASparseVector) source);
		int srcLength = source.length();
		if (srcLength==0) throw new IllegalArgumentException("Can't create a length 0 SparseIndexedVector");
		int[] indexes=source.nonZeroIndices();
		int len=indexes.length;
		double[] vals=new double[len];
		source.getElements(vals,0,indexes);
		return wrap(srcLength,Index.wrap(indexes),vals);
	}
	
	public static SparseIndexedVector create(ASparseVector source) {
		int length = source.length();
		if (length==0) throw new IllegalArgumentException("Can't create a length 0 SparseIndexedVector");
		Index ixs=source.nonSparseIndex();
		AVector nsv=source.nonSparseValues();
		return wrap(length,ixs,nsv.toDoubleArray());
	}
	
	public static SparseIndexedVector create(SparseHashedVector source) {
		int length = source.length();
		if (length==0) throw new IllegalArgumentException("Can't create a length 0 SparseIndexedVector");
		Index ixs=source.nonSparseIndex();
		int n=ixs.length();
		double[] vals=new double[n];
		for (int i=0; i<n; i++) {
			vals[i]=source.unsafeGet(ixs.unsafeGet(i));
		}
		return wrap(length,ixs,vals);
	}
	
	/** Creates a SparseIndexedVector from a row of an existing matrix */
	public static AVector createFromRow(AMatrix m, int row) {
		if (m instanceof AVectorMatrix) return create(m.getRow(row));
		return create(m.getRow(row));
	}
	
	@Override
	public int nonSparseElementCount() {
		return data.length;
	}

    public AVector innerProduct(SparseRowMatrix m) {
		int cc = m.columnCount();
		int rc = m.rowCount();
		checkLength(rc);
        ASparseIndexedVector r = SparseIndexedVector.createLength(cc);
        int n=nonSparseElementCount();
		for (int ii = 0; ii < n; ii++) {
			double value=data[ii]; 
			if (value==0.0) continue; // skip zero values
			int i=index.get(ii);
            AVector row = m.unsafeGetVector(i);
            if (row==null) continue; // skip zero rows
            
            // TODO: we were casting to ASparseVector, necessary for speed??
            //            if (row instanceof ASparseVector)
            // This vector could be of any type, such as Vector3.
            // And some vectors don't have a sparseClone and instead return
            // a reference to same instance!!!! (See Vector3...)
            r.addMultiple(row, value);
		}
		return r;
	}

    /**
     * Specialised overloaded innerProduct for multiplication with matrices that support fast column access
     * @param m
     * @return
     */
    public AVector innerProduct(IFastColumns m) {
		int cc = m.columnCount();
		int rc = m.rowCount();
		checkLength(rc);
        ASparseIndexedVector r = SparseIndexedVector.createLength(cc);
		for (int i = 0; i < cc; i++) {
			r.unsafeSet(i, this.dotProduct((ASparseVector)m.getColumn(i)));
		}
		return r;
	}
    
    @Override
    public AVector innerProduct(AMatrix m) {
        if (m instanceof SparseRowMatrix) {
            return this.innerProduct((SparseRowMatrix)m);
        }
        if (m instanceof IFastColumns) {
            return this.innerProduct((IFastColumns)m);
        }
		int cc=m.columnCount();
		int rc=m.rowCount();
		checkLength(rc);
        AVector r = SparseIndexedVector.createLength(cc);
		for (int i=0; i<cc; i++) {
			r.unsafeSet(i,this.dotProduct(m.getColumn(i)));
		}
		return r;
	}

    @Override
	public void add(AVector v) {
		if (v instanceof ASparseVector) {
			add((ASparseVector)v);
			return;
		}
		includeIndices(v);	
		for (int i=0; i<data.length; i++) {
			data[i]+=v.unsafeGet(index.get(i));
		}
	}
    
    @Override
	public void addMultiple(AVector v,double factor) {
    	if (factor==0.0) return;
		if (v instanceof ASparseVector) {
			addMultiple(((ASparseVector)v).toSparseIndexedVector(),factor);
			return;
		}
		super.addMultiple(v, factor);
	}

	@Override
	public void add(double[] src, int srcOffset) {
		int[] nixs=DoubleArrays.nonZeroIndices(src,srcOffset,length);
		includeIndices(nixs);
		// data array now contains new indices
		for (int i=0; i<data.length; i++) {
			int ix=index.get(i);
			double sv=src[srcOffset+ix];
			if (sv!=0.0) data[i]+=src[srcOffset+ix]; // TODO: benchamark does conditional help??
		}
	}
	
	@Override
	public void add(ASparseVector v) {
		checkSameLength(v);
        if (v instanceof ZeroVector) {
            return;
        }
		Index vix=includeIndices(v);	
		AVector vns=v.nonSparseValues();
		int n=vix.length();
		for (int i=0; i<n; i++) {
			int ix=vix.get(i);
			addAt(ix,vns.unsafeGet(i));
		}
	}
	
	@Override
	public void addMultiple(SparseIndexedVector v, double factor) {
		checkSameLength(v);
        if (factor==0.0) return;
		Index vix=includeIndices(v);	
		AVector vns=v.nonSparseValues();
		int n=vix.length();
		for (int i=0; i<n; i++) {
			int ix=vix.get(i);
			addAt(ix,vns.unsafeGet(i)*factor);
		}
	}
    
    @Override
	public void sub(AVector v) {
		addMultiple(v,-1.0);
	}

	@Override
	public void multiply (double d) {
		if (d==0.0) {
			data=DoubleArrays.EMPTY;
			index=Index.EMPTY;
		} else {
			DoubleArrays.multiply(data, d);
		}
	}
	
	@Override
	public void pow(double exponent) {
		DoubleArrays.pow(data, exponent);
	}
	
	@Override
	public void square() {
		DoubleArrays.square(data);
	}
	
	@Override
	public void multiply(AVector v) {
		if (v instanceof ADenseArrayVector) {
			multiply((ADenseArrayVector)v);
			return;
		} else if (v instanceof ASparseVector) {
			multiply((ASparseVector)v);
		} else {
			checkSameLength(v);
			double[] data=this.data;
			int[] ixs=index.data;
			for (int i=0; i<data.length; i++) {
				data[i]*=v.unsafeGet(ixs[i]);
			}
		}
	}
	
	public void multiply(ASparseVector v) {
		checkSameLength(v);
		int[] thisIndex=index.data;
		int[] thatIndex=v.nonSparseIndex().data;
		int[] tix=IntArrays.intersectSorted(thatIndex, thisIndex);
		int n=tix.length;
		double[] ndata=new double[n];
		int i1=0;
		int i2=0;
		for (int i=0; i<n; i++) {
			int ti=tix[i];
			while (thatIndex[i1]!=ti) i1++;
			while (thisIndex[i2]!=ti) i2++;
			ndata[i]=v.unsafeGet(thatIndex[i1])*unsafeGet(thisIndex[i2]);
		}
		this.data=ndata;
		this.index=Index.wrap(tix);
	}
	
	@Override
	public void multiply(ADenseArrayVector v) {
		multiply(v.getArray(),v.getArrayOffset());
	}
	
	@Override
	public SparseIndexedVector multiplyCopy(double factor) {
		return create(length,index,DoubleArrays.multiplyCopy(data, factor));
	}
	
	@Override
	public void multiply(double[] array, int offset) {
		double[] data=this.data;
		int[] ixs=index.data;
		for (int i=0; i<data.length; i++) {
			data[i]*=array[offset+ixs[i]];
		}
	}
	
	@Override
	public double maxAbsElement() {
		double[] data=this.data;
		double result=0.0;
		for (int i=0; i<data.length; i++) {
			double d=Math.abs(data[i]);
			if (d>result) result=d; 
		}
		return result;
	}
	
	@Override
	public int maxElementIndex(){
		double[] data=this.data;
		if (data.length==0) return 0;
		double result=data[0];
		int di=0;
		for (int i=1; i<data.length; i++) {
			double d=data[i];
			if (d>result) {
				result=d; 
				di=i;
			}
		}
		if (result<0.0) { // see if we can find a zero element
			int ind=index.findMissing();
			if (ind>0) return ind;
		}
		return index.get(di);
	}
	
 
	@Override
	public int maxAbsElementIndex(){
		double[] data=this.data;
		if (data.length==0) return 0;
		double result=data[0];
		int di=0;
		for (int i=1; i<data.length; i++) {
			double d=Math.abs(data[i]);
			if (d>result) {
				result=d; 
				di=i;
			}
		}
		return index.get(di);
	}
	
	@Override
	public int minElementIndex(){
		double[] data=this.data;
		if (data.length==0) return 0;
		double result=data[0];
		int di=0;
		for (int i=1; i<data.length; i++) {
			double d=data[i];
			if (d<result) {
				result=d; 
				di=i;
			}
		}
		if (result>0.0) { // see if we can find a zero element
			int ind=index.findMissing();
			if (ind>=0) return ind;
		}
		return index.get(di);
	}
	
	@Override
	public void negate() {
		DoubleArrays.negate(data);
	}
	
	@Override
	public void applyOp(Op op) {
		int dlen=data.length;
		if ((dlen<length())&&(op.isStochastic()||(op.apply(0.0)!=0.0))) {
			super.applyOp(op);
		} else {
			op.applyTo(data);
		}
	}
	
	@Override
	public void applyOp(Op2 op, double d) {
		int dlen=data.length;
		if ((dlen<length())&&(op.isStochastic()||(op.apply(0.0,d)!=0.0))) {
			super.applyOp(op,d);
		} else {
			op.applyTo(data,d);
		}
	}
	
	@Override
	public double reduce(Op2 op, double init) {
		double[] data=this.data;
		int[] ixs=index.data;
		int n=data.length;
		double result=init;
		int start=0;
		for (int i=0; i<n; i++) {
			int ix=ixs[i];
			double v=data[i];
			result=op.reduceZeros(result,ix-start);
			result=op.apply(result, v);
			start=ix+1;
		}
		return op.reduceZeros(result,length-start); // reduce over any remaining zeros
	}
	
	@Override
	public double reduce(Op2 op) {
		double[] data=this.data;
		int[] ixs=index.data;
		int n=data.length;
		if (n==0) return op.reduceZeros(length);
		double result=unsafeGet(0);
		int start=1;
		int starti=(ixs[0]==0)?1:0; // start at data array element 0 if not at index 0, 1 otherwise
		for (int i=starti; i<n; i++) {
			int ix=ixs[i];
			double v=data[i];
			result=op.reduceZeros(result,ix-start);
			result=op.apply(result, v);
			start=ix+1;
		}
		return op.reduceZeros(result,length-start); // reduce over any remaining zeros
	}
	
	@Override
	public void abs() {
		DoubleArrays.abs(data);
	}

	@Override
	public double get(int i) {
		checkIndex(i);
		int ip=index.indexPosition(i);
		if (ip<0) return 0.0;
		return data[ip];
	}
	
	@Override
	public double unsafeGet(int i) {
		int ip=index.indexPosition(i);
		if (ip<0) return 0.0;
		return data[ip];
	}
	
	@Override
	public boolean isFullyMutable() {
		return true;
	}
	
	@Override
	public boolean isMutable() {
		return length>0;
	}
	
	@Override 
	public void setElements(double[] array, int offset) {
		int nz=DoubleArrays.nonZeroCount(array, offset, length);
		int[] ixs=new int[nz];
		double[] data=new double[nz];
		this.data=data;
		int di=0;
		for (int i=0; i<length; i++) {
			double x=array[offset+i];
			if (x==0.0) continue;
			ixs[di]=i;
			data[di]=x;
			di++;
		}
		index=Index.wrap(ixs);
	}
	
	@Override 
	public void setElements(int pos,double[] array, int offset, int length) {
		if (length>=this.length) {
			setElements(array,offset);
			return;
		}
		
		int nz=DoubleArrays.nonZeroCount(array, offset, length);
		int[] ixs=new int[nz];
		double[] data=new double[nz];
		this.data=data;
		int di=pos;
		for (int i=0; i<length; i++) {
			double x=array[offset+i];
			if (x==0.0) continue;
			ixs[di]=i;
			data[di]=x;
			di++;
		}
		index=Index.wrap(ixs);
	}
	
	@Override
	public void set(AVector v) {
		checkSameLength(v);
		
		if (v instanceof ADenseArrayVector) {
			set((ADenseArrayVector)v);
			return;
		} else if (v instanceof ASparseVector) {
            int[] nzi = v.nonZeroIndices();
            index=Index.wrap(nzi);
            if (nzi.length!=data.length) {
                data=new double[nzi.length];
            }
            for (int i=0; i<index.length(); i++) {
                double val=v.unsafeGet(index.get(i));
                data[i]=val;
            }
            return;
        } else {
            double[] data=this.data;
            int nz=(int) v.nonZeroCount();
            if (nz!=data.length) {
                data=new double[nz];
                this.data=data;
                index=Index.createLength(nz);
            }
            
            int di=0;
            for (int i=0; i<nz; i++) {
                double val=v.unsafeGet(i);
                if (val!=0) {
                    data[di]=val;
                    index.set(di, i);
                    di++;
                }
            }
        }
	}
	
	@Override
	public void set(ADenseArrayVector v) {
		checkSameLength(v);
		setElements(v.getArray(),v.getArrayOffset());
	}

	@Override
	public void set(int i, double value) {
		checkIndex(i);
		unsafeSet(i,value);
	}
	
	@Override
	public void unsafeSet(int i, double value) {
		int ip=index.indexPosition(i);
		if (ip<0) {
			if (value==0.0) return;
			int npos=index.seekPosition(i);
			data=DoubleArrays.insert(data,npos,value);
			index=index.insert(npos,i);
		} else {
			data[ip]=value;
		}
	}
	
	@Override
	public void addAt(int i, double value) {
		if (value==0.0) return; // no change required
		int ip=index.indexPosition(i);
		if (ip<0) {
			if (value==0.0) return;
			int npos=index.seekPosition(i);
			data=DoubleArrays.insert(data,npos,value);
			index=index.insert(npos,i);
		} else {
			data[ip]+=value;
		}
	}

    // TODO: consider a generic sparseApplyOp instead.
    //       keep in mind efficiency when randomly
    //       modifying index.
    @Override
    public ASparseVector roundToZero(double precision) {
        int[] aboveInds = new int[data.length];
        double[] aboveData = new double[data.length];
        int ai = 0;
        for (int i = 0; i < index.length(); i++) {
            if (data[i] > precision) {
                aboveInds[ai] = index.get(i);
                aboveData[ai] = data[i];
                ai++;
            }
        }
        int[] newInds = new int[ai];
        double[] newData = new double[ai];
        System.arraycopy(aboveInds, 0, newInds, 0, ai);
        System.arraycopy(aboveData, 0, newData, 0, ai);

        return SparseIndexedVector.wrap(this.length, newInds, newData);
    }

	@Override
	public Vector nonSparseValues() {
		return Vector.wrap(data);
	}
	
	@Override
	public Index nonSparseIndex() {
		return index;
	}

	
	@Override
	public Vector toVector() {
		Vector v=Vector.createLength(length);
		double[] data=this.data;
		int[] ixs=index.data;
		for (int i=0; i<data.length; i++) {
			v.unsafeSet(ixs[i],data[i]);
		}	
		return v;
	}
	
	@Override
	public SparseIndexedVector toSparseIndexedVector() {
		return this;
	}
	
	@Override
	public SparseIndexedVector clone() {
		return exactClone();
	}
	
	/**
	 * Include additional indices in the non-sparse index set of this vector.
	 * 
	 * Useful to improve performance if subsequent operations will access these indices.
	 * 
	 * @param ixs
	 */
	protected void includeIndices(int[] ixs) {
		int[] nixs = IntArrays.mergeSorted(index.data,ixs);
		if (nixs.length==index.length()) return; // no new indices
		int nl=nixs.length;
		double[] data=this.data;
		double[] ndata=new double[nl];
		int si=0;
        
		for (int i=0; i<nl; i++) {
            if (si>=data.length) break;
			int z=index.data[si];
			if (z==nixs[i]) {
				ndata[i]=data[si];
				si++; 
			}
		}
		this.data=ndata;
		index=Index.wrap(nixs);
	}
	
	/**
	 * Include additional indices in the non-sparse index set of this vector.
	 * 
	 * Useful to improve performance if subsequent operations will access these indices.
	 * 
	 * @param ixs
	 */
	public void includeIndices(Index ixs) {
		includeIndices(ixs.data);
	}
	
	/**
	 * Include additional indices in the non-sparse index set of this vector.
	 * 
	 * Useful to improve performance if subsequent operations will access these indices.
	 * 
	 * @param ixs
	 * @return the newly included indices from the vector v
	 */
	protected Index includeIndices(AVector v) {
		Index ix=v.nonSparseIndex();
		includeIndices(ix);
		return ix;
	}
	
	@Override
	public SparseIndexedVector sparseClone() {
		return exactClone();
	}
	
	@Override
	public SparseIndexedVector exactClone() {
		return new SparseIndexedVector(length,index.clone(),data.clone());
	}
	
	@Override
	public void validate() {
		if (index.length()!=data.length) throw new VectorzException("Inconsistent data and index!");
		if (!index.isDistinctSorted()) throw new VectorzException("Invalid index: "+index);
		super.validate();
	}
	
	@Override
	public AVector immutable() {
		return SparseImmutableVector.create(this);
	}

	@Override
	double[] internalData() {
		return data;
	}

	@Override
	Index internalIndex() {
		return index;
	}



}


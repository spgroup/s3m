package org.wikibrain.spatial.loader; 


import com.google.common.collect.*; 
import com.vividsolutions.jts.geom.*; 
import com.vividsolutions.jts.geom.Geometry; 
import com.vividsolutions.jts.geom.GeometryFactory; 
import de.tudarmstadt.ukp.wikipedia.parser.Link; 
import net.lingala.zip4j.core.ZipFile; 
import net.lingala.zip4j.exception.ZipException; 

import org.apache.commons.io.FileUtils; 

import org.geotools.data.simple.SimpleFeatureIterator; 

import org.geotools.data.DefaultTransaction; 
import org.geotools.data.Transaction; 
import org.geotools.data.collection.ListFeatureCollection; 
import org.geotools.data.shapefile.ShapefileDataStore; 
import org.geotools.data.shapefile.ShapefileDataStoreFactory; 
import org.geotools.data.simple.SimpleFeatureCollection; 
import org.geotools.data.simple.SimpleFeatureSource; 
import org.geotools.data.simple.SimpleFeatureStore; 
import org.geotools.data.*; 
import org.geotools.feature.simple.SimpleFeatureBuilder; 
import org.geotools.feature.simple.SimpleFeatureTypeBuilder; 
import org.geotools.geometry.jts.JTSFactoryFinder; 
import org.geotools.referencing.crs.DefaultGeographicCRS; 
import org.opengis.feature.simple.SimpleFeature; 
import org.opengis.feature.simple.SimpleFeatureType; 

import org.wikibrain.core.WikiBrainException; 

import org.wikibrain.spatial.core.constants.RefSys; 
import org.wikibrain.utils.ParallelForEach; 
import org.wikibrain.utils.Procedure; 
import org.wikibrain.utils.WpIOUtils; 

import org.wikibrain.download.*; 


import java.io.*; 
import java.net.MalformedURLException; 
import java.net.URL; 

import java.util.*; 
import java.util.List; 
import java.util.concurrent.ConcurrentLinkedQueue; 
 
import java.util.concurrent.atomic.AtomicInteger; 
import java.util.logging.Level; 
import java.util.logging.Logger; 


/**
 * Created by aaroniidx on 4/13/14.
 */
public  class  GADMConverter {
	


    public static final Logger LOG = Logger.getLogger(GADMConverter.class.getName());
	

    public void downloadAndConvert(SpatialDataFolder folder) throws WikiBrainException {

        try {


            WpIOUtils ioUtils = new WpIOUtils();
            String tmpFolderName = "_gadmdownload";

            File tmpFolder = WpIOUtils.createTempDirectory(tmpFolderName, true);


            // Download to a temp folder (Note that WikiBrain will ignore all reference systems that begin with "_"
            //folder.createNewReferenceSystemIfNotExists(tmpFolder.getCanonicalPath());
            File rawFile = downloadGADMShapeFile(tmpFolder.getCanonicalPath());
            //File rawFile = new File("tmp/gadm_v2_shp/gadm2.shp");

            //copy level 2 shapefile to earth reference system
            LOG.log(Level.INFO, "Copying level 2 shapefiles to " + folder.getRefSysFolder("earth").getCanonicalPath());
            FileUtils.copyDirectory(new File(tmpFolder.getCanonicalPath()), folder.getRefSysFolder("earth"));

            // convert file and save as layer in earth reference system
            LOG.log(Level.INFO, "Start mapping level 1 shapefiles.");
            convertShpFile(rawFile, folder, 1);
            LOG.log(Level.INFO, "Start mapping level 0 shapefiles.");
            convertShpFile(rawFile, folder, 0);


        } catch (Exception e) {
            throw new WikiBrainException(e);
        } finally {
            folder.deleteSpecificFile("read_me.pdf", RefSys.EARTH);
            folder.deleteLayer("gadm2", RefSys.EARTH);
        }


    }
	

    /**
     * Download GADM shape file
     *
     * @param tmpFolder
     * @return
     */
    public File downloadGADMShapeFile(String tmpFolder) throws IOException, ZipException, InterruptedException {

        String baseFileName = "gadm_v2_shp";
        String zipFileName = baseFileName + ".zip";
        String gadmURL = "http://biogeo.ucdavis.edu/data/gadm2/" + zipFileName;
        File gadmShapeFile = new File(tmpFolder + "/" + zipFileName);

        FileDownloader downloader = new FileDownloader();
        downloader.download(new URL(gadmURL), gadmShapeFile);
        ZipFile zipFile = new ZipFile(gadmShapeFile.getCanonicalPath());
        LOG.log(Level.INFO, "Extracting to " + gadmShapeFile.getParent());
        zipFile.extractAll(gadmShapeFile.getParent());
        File f = new File(tmpFolder + "/gadm2.shp");
        LOG.log(Level.INFO, "Extraction complete.");
        gadmShapeFile.delete();
        return f;


    }
	

    private AtomicInteger countryCount = new AtomicInteger(0);
	

    
	
    private List<String> exceptionList;
	


    /**
     * @param outputFolder
     * @param level        //TODO: reduce memory usage
     *                     Converts raw GADM shapefile into WikiBrain readable type
     *                     Recommended JVM max heapsize = 4G
     */


    public void convertShpFile(File rawFile, SpatialDataFolder outputFolder, int level) throws IOException, WikiBrainException {
        if ((level != 0) && (level != 1)) throw new IllegalArgumentException("Level must be 0 or 1");

        File outputFile = new File(outputFolder.getRefSysFolder("earth").getCanonicalPath() + "/" + "gadm" + level + ".shp");
        ListMultimap<String, String> countryStatePair = ArrayListMultimap.create();

        final SimpleFeatureType WIKITYPE = getOutputFeatureType(level);

        final SimpleFeatureSource outputFeatureSource = getOutputDataFeatureSource(outputFile, WIKITYPE);

        final Transaction transaction = new DefaultTransaction("create");

        final SimpleFeatureCollection inputCollection = getInputCollection(rawFile);
        SimpleFeatureIterator inputFeatures = inputCollection.features();

        final ConcurrentLinkedQueue<List<SimpleFeature>> writeQueue = new ConcurrentLinkedQueue<List<SimpleFeature>>();

        try {

            while (inputFeatures.hasNext()) {
                SimpleFeature feature = inputFeatures.next();
                String country = ((String) feature.getAttribute(4)).intern();
                String state = ((String) feature.getAttribute(6)).intern();
                if (!countryStatePair.containsEntry(country, state))
                    countryStatePair.put(country, state);
            }

            final Multimap<String, String> countryState = countryStatePair;

            inputFeatures.close();

            exceptionList = new ArrayList<String>();

            LOG.log(Level.INFO, "Start processing polygons for level " + level + " administrative districts.");



            if (level == 1) {
                for (String country : countryState.keySet()) {

                    ParallelForEach.loop(countryState.get(country), new Procedure<String>() {
                        @Override
                        public void call(String state) throws Exception {

                            List<SimpleFeature> features = inputFeatureHandler(inputCollection, state, 1, WIKITYPE, countryState);
                            writeQueue.add(features);
                            writeToShpFile(outputFeatureSource, WIKITYPE, transaction, writeQueue.poll());
                        }
                    });
                }


            } else {

                ParallelForEach.loop(countryState.keySet(), new Procedure<String>() {
                    @Override
                    public void call(String country) throws Exception {

                        List<SimpleFeature> features = inputFeatureHandler(inputCollection, country, 0, WIKITYPE, countryState);
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_7035804680523641562.java
                        writeQueue.add(features);
                        writeToShpFile(outputFeatureSource, WIKITYPE, transaction, writeQueue.poll());
                        ;
=======
                        writeQueue.push(features);
                        writeToShpFile(outputFeatureSource, WIKITYPE, transaction, writeQueue.pop());
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_8254491233405647124.java

                    }
                });

                LOG.log(Level.INFO, "Start processing polygons where exceptions occurred.");
                int count = 0;
                for (String country: exceptionList) {
                    count++;
                    LOG.log(Level.INFO, "Combining polygons for " + country + "(" + count + "/" + exceptionList.size() + ")");
                    List<SimpleFeature> features = inputFeatureHandler(inputCollection, country, 0, WIKITYPE, countryState);
                    writeToShpFile(outputFeatureSource, WIKITYPE, transaction, features);
                }


            }


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            transaction.close();
            countryCount.set(0);
        }


    }
	

    private List<SimpleFeature> inputFeatureHandler(SimpleFeatureCollection inputCollection, String featureName, int level, SimpleFeatureType outputFeatureType, Multimap<String, String> relation) {

        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        List<Geometry> geometryList = new ArrayList<Geometry>();
        SimpleFeatureIterator inputFeatures = inputCollection.features();
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(outputFeatureType);
        Multimap<String, String> reverted = ArrayListMultimap.create();
        Geometry newGeom = null;
        String country;


        if (!exceptionList.contains(featureName)) {
            if (level == 1) {
                country = (String) Multimaps.invertFrom(relation, reverted).get(featureName).toArray()[0];
                synchronized (this) {LOG.log(Level.INFO, "Combining polygons for level 1 administrative district: " + featureName + " in " + country + " (" + countryCount.incrementAndGet() + "/" + relation.keySet().size() + ")");}
            } else {
                country = featureName;
                synchronized (this) {LOG.log(Level.INFO, "Combining polygons for " + country + " (" + countryCount.incrementAndGet() + "/" + relation.keySet().size() + ")");}
            }
        }

        while (inputFeatures.hasNext()) {
            SimpleFeature feature = inputFeatures.next();
            if (level == 1) {
                if (feature.getAttribute(6).equals(featureName)) geometryList.add((Geometry) feature.getAttribute(0));
            } else if (feature.getAttribute(4).equals(featureName))
                geometryList.add((Geometry) feature.getAttribute(0));
        }
        inputFeatures.close();

        try {
            newGeom = geometryFactory.buildGeometry(geometryList).union().getBoundary();

        } catch (Exception e) {
            LOG.log(Level.INFO, "Exception occurred at " + featureName + ": " + e.getMessage() + ". Attempting different combining methods.");
            if (level == 1 || exceptionList.contains(featureName))
                newGeom = geometryFactory.buildGeometry(geometryList).buffer(0).getBoundary();
            else exceptionList.add(featureName);

        }

        featureBuilder.add(newGeom);
        if (level == 1) {
            featureBuilder.add(featureName);
            featureBuilder.add(featureName + ", " + Multimaps.invertFrom(relation, reverted).get(featureName).toArray()[0]);
        } else
            featureBuilder.add(featureName);
        SimpleFeature feature = featureBuilder.buildFeature(null);

        List<SimpleFeature> features = new ArrayList<SimpleFeature>();
        features.add(feature);

        return features;

    }
	

    private synchronized void writeToShpFile(SimpleFeatureSource outputFeatureSource, SimpleFeatureType outputFeatureType, Transaction transaction, List<SimpleFeature> features) throws IOException {
        if (outputFeatureSource instanceof SimpleFeatureStore) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) outputFeatureSource;

            SimpleFeatureCollection collection = new ListFeatureCollection(outputFeatureType, features);
            featureStore.setTransaction(transaction);
            try {
                featureStore.addFeatures(collection);
                transaction.commit();
            } catch (Exception e) {
                e.printStackTrace();
                transaction.rollback();
            }
        } else {
            LOG.log(Level.INFO, "WIKITYPE does not support read/write access");
        }

    }
	

    private SimpleFeatureSource getOutputDataFeatureSource(File outputFile, SimpleFeatureType type) throws IOException {

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        Map<String, Serializable> outputParams = new HashMap<String, Serializable>();
        outputParams.put("url", outputFile.toURI().toURL());
        outputParams.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore outputDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(outputParams);
        outputDataStore.createSchema(type);
        String typeName = outputDataStore.getTypeNames()[0];
        return outputDataStore.getFeatureSource(typeName);

    }
	

    private SimpleFeatureCollection getInputCollection(File rawFile) throws IOException {

        Map<String, URL> map = new HashMap<String, URL>();
        map.put("url", rawFile.toURI().toURL());
        DataStore inputDataStore = DataStoreFinder.getDataStore(map);
        SimpleFeatureSource inputFeatureSource = inputDataStore.getFeatureSource(inputDataStore.getTypeNames()[0]);
        return inputFeatureSource.getFeatures();


    }
	

    private SimpleFeatureType getOutputFeatureType(int level) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("WIKITYPE");
        typeBuilder.setCRS(DefaultGeographicCRS.WGS84);
        typeBuilder.add("the_geom", MultiPolygon.class);
        typeBuilder.add("TITLE1_EN", String.class);
        if (level == 1) typeBuilder.add("TITLE2_EN", String.class);
        typeBuilder.setDefaultGeometry("the_geom");

        return typeBuilder.buildFeatureType();

    }

}


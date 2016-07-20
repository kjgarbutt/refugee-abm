package refugee;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;

import sim.field.continuous.Continuous2D;
import sim.field.geo.GeomVectorField;
import sim.field.grid.SparseGrid2D;
import sim.field.network.Edge;
import sim.field.network.Network;
import sim.io.geo.ShapeFileImporter;
import sim.portrayal.grid.SparseGridPortrayal2D;
import sim.util.Bag;
import sim.util.Double2D;
import sim.util.Int2D;
import sim.util.geo.MasonGeometry;
import net.sf.csv4j.*;
import org.apache.commons.math3.distribution.NormalDistribution;

class MigrationBuilder {
    public static Migration migrationSim;
    private static NormalDistribution nd = new NormalDistribution(Parameters.AVG_FAMILY_SIZE, Parameters.FAMILY_SIZE_SD);
    private static HashMap<Integer, ArrayList<Double>> age_dist;

    //public static HashSet<Geometry> removeGeometry = new HashSet<Geometry>();
    //public static HashSet<LineString> allLineStrings = new HashSet<LineString>();
	
    //initialize world

	//public static void initializeWorld(Migration sim, String popPath, String adminPath, String ageDistPath){
    
    public static void initializeWorld(Migration sim){	

		migrationSim = sim;
		
	    age_dist = new HashMap<Integer, ArrayList<Double>>();
		String[] cityAttributes = {"ID","NAME_1", "ORIG", "POP", "SPOP", "QUOTA_1", "VIOL_1", "ECON_1", "FAMILY_1"};
		String[] roadAttributes = {"ID", "FR", "TO", "SPEED_1", "POP", "COST_1", "TLEVEL_1", "DEATHS_1","LENGTH_1"};
		String[] regionAttributes = {"REGION", "SQKM"};
	//	migrationSim.worldPopResolution = new SparseGrid2D();
        //age_dist = new HashMap<Integer, ArrayList<Double>>();
		migrationSim.world_height = 500; //9990;  //TODO - set correct size
		migrationSim.world_width = 500; //9390;	//TODO - set correct size

		migrationSim.roadNetwork = new Network();
		migrationSim.allRoadNodes = new SparseGrid2D(sim.world_width, sim.world_height);

		migrationSim.roadLinks = new GeomVectorField(sim.world_width, sim.world_height);
		//migrationSim.roadLinks = new GeomVectorField(1,1);
	    Bag roadAtt = new Bag(roadAttributes);
	    
		migrationSim.regions = new GeomVectorField(sim.world_width, sim.world_height);
	    Bag regionAtt = new Bag(regionAttributes);
	    
	    migrationSim.cityPoints = new GeomVectorField(sim.world_width, sim.world_height);
	    Bag cityAtt = new Bag(cityAttributes);
	    
	    migrationSim.cityGrid = new SparseGrid2D(sim.world_width, sim.world_height);
	    //Bag cityAtt = new Bag(cityAttributes);
	    
	    //try{
	    String[] files = {Parameters.REGION_SHP, Parameters.ROAD_SHP, Parameters.CITY_SHP};//shapefiles
	    Bag[] attfiles = {regionAtt, roadAtt, cityAtt};
	    GeomVectorField[] vectorFields = {migrationSim.regions, migrationSim.roadLinks, migrationSim.cityPoints};
	    readInShapefile(files, attfiles, vectorFields);//read in attributes
	    
	    //expand the extent to include all features
	    Envelope MBR = migrationSim.regions.getMBR();
	    MBR.expandToInclude(migrationSim.roadLinks.getMBR());
	    MBR.expandToInclude(migrationSim.cityPoints.getMBR());
	    
	    migrationSim.regions.setMBR(MBR);
	    migrationSim.roadLinks.setMBR(MBR);
	    migrationSim.cityPoints.setMBR(MBR);
	    
	    //InputStream inputStream = new FileInputStream();//COMMENT OUT to change inputter
	    //}
	    //catch(FileNotFoundException e)
	    //{
	    //	e.printStackTrace();
	    //}
	    makeCities(migrationSim.cityPoints, migrationSim.cityGrid, migrationSim.cities,migrationSim.cityList);
	    extractFromRoadLinks(migrationSim.roadLinks, migrationSim);
        setUpAgeDist(Parameters.AGE_DIST);
	    //read in structures
        addRefugees();
	   // printCities();
	}
	
    private static void printCities() {
		for (Object city: migrationSim.cities){
			City c = (City)city;
			System.out.format("Name: " + c.getName() + " Ref Pop: " + c.getRefugeePopulation());
			System.out.println("\n");
		}
		
	}

	public static class Node
    {
        public Int2D location; 

        ArrayList<Edge> links;
        double weightOnLineString;//measures the weight on the line string from 0
        public HashSet<LineString> lineStrings = new HashSet<LineString>();
        public int index;
        public Node(Int2D l)
        {
            location = l;
            links = new ArrayList<Edge>();
        }

        public ArrayList<Edge> getLinks() {
            return links;
        }
        @Override
        public String toString()
        {
            return "(" + location.getX() + ", " + location.getY() + ")";
        }
        //
    }
    
    static void makeCities(GeomVectorField cities_vector, SparseGrid2D grid, Bag addTo,Map<Integer, City> cityList){
    	Bag cities = cities_vector.getGeometries();
    
    	Envelope e = cities_vector.getMBR();
    	//Envelope e = migrationSim.regions.getMBR();
    	double xmin = e.getMinX(), ymin = e.getMinY(), xmax = e.getMaxX(), ymax = e.getMaxY();
    	int xcols = migrationSim.world_width - 1, ycols = migrationSim.world_height - 1;
    	System.out.println("Reading in Cities");
    	for (int i = 0; i < cities.size(); i++)
    	{
    		MasonGeometry cityinfo= (MasonGeometry)cities.objs[i];
    	
		//String[] cityAttributes = {"ID","NAME", "ORIG_1", "POP", "QUOTA", "VIOL_1", "ECON_1", "FAMILY_1"};
    	
    		Point point = cities_vector.getGeometryLocation(cityinfo);
    		double x = point.getX(), y = point.getY();
    		int xint = (int) Math.floor(xcols * (x - xmin) / (xmax - xmin)), yint = (int) (ycols - Math.floor(ycols * (y - ymin) / (ymax - ymin))); // REMEMBER TO FLIP THE Y VALUE
    		String name = cityinfo.getStringAttribute("NAME_1");
    		int ID = cityinfo.getIntegerAttribute("ID");
    		int origin = cityinfo.getIntegerAttribute("ORIG");
	    	double scaledPop = cityinfo.getDoubleAttribute("SPOP");
	    	int pop = cityinfo.getIntegerAttribute("POP");
	    	int quota = cityinfo.getIntegerAttribute("QUOTA_1");
	    	double violence = cityinfo.getDoubleAttribute("VIOL_1");
	    	double economy = cityinfo.getDoubleAttribute("ECON_1");
	    	double familyPresence = cityinfo.getDoubleAttribute("FAMILY_1");
	    	Int2D location = new Int2D(xint, yint);
    	
	    	City city = new City(location, ID, name, origin, scaledPop, pop, quota, violence, economy, familyPresence);
	    	addTo.add(city);
	    	cityList.put(ID,city);
	        grid.setObjectLocation(city, location);
    	}
    }
    static void readInShapefile(String[] files, Bag[] attfiles, GeomVectorField[] vectorFields)
    {
        try
        {
            for(int i = 0; i < files.length; i++)
            {
            	Bag attributes = attfiles[i];
                String filePath = files[i];
                File file = new File(filePath);
                URL shapeURI = file.toURI().toURL();
                ShapeFileImporter.read(shapeURI, vectorFields[i], attributes);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private static void addRefugees() 
    {
        System.out.println("Adding Refugees ");
    	migrationSim.world = new Continuous2D(Parameters.WORLD_DISCRETIZTION, migrationSim.world_width, migrationSim.world_height); //TODO set this correctly
    	//migrationSim.world2 = new SparseGrid2D(migrationSim.world_width, migrationSim.world_height);
           for (Object c : migrationSim.cities){
        	   
        	   City city = (City)c;
           // InputStream inputstream = new FileInputStream(pop_file);
           
        	if (city.getOrigin() == 1){
                int currentPop = 0;//1,4,5,10,3,14,24
	           // while  (currentPop + 5 <= city.getQuota()){//max family size here: 5
                while  (currentPop <= Parameters.NUM_ORIG_REFUGEES) { //test refugee points 
		            RefugeeFamily r = createRefugeeFamily(city);
		            System.out.println(r.getFamily().size());
	            	for (Refugee refugee: r.getFamily()){
	            		currentPop++;
	            		city.addMember(refugee);
	            		//System.out.println(refugee.getHome().getName());
		            	//System.out.println(city.getRefugeePP);
	            		migrationSim.refugees.add(refugee);
	            		Int2D loc = city.getLocation();
                        double y_coord = (loc.y*Parameters.WORLD_TO_POP_SCALE) + (int)(migrationSim.random.nextDouble() * Parameters.WORLD_TO_POP_SCALE);
                        double x_coord = (loc.x*Parameters.WORLD_TO_POP_SCALE) + (int)(migrationSim.random.nextDouble() * Parameters.WORLD_TO_POP_SCALE);
                        migrationSim.world.setObjectLocation(r, new Double2D(x_coord, y_coord));
                        int y_coordint = loc.y + (int)((migrationSim.random.nextDouble() - 0.5) * 5);
                        int x_coordint = loc.x + (int)((migrationSim.random.nextDouble() - 0.5 ) * 5);
            
                       // migrationSim.world2.setObjectLocation(r,new Int2D (x_coordint, y_coordint));
                        migrationSim.total_pop++;
	            	}
            		migrationSim.schedule.scheduleRepeating(r);

	            }
	            
	         }

         }
    }
    
    private static RefugeeFamily createRefugeeFamily(City city)
    {

    	//generate family
    	int familySize = pickFamilySize(); 
        double finStatus = pick_fin_status();
    	RefugeeFamily refugeeFamily = new RefugeeFamily(city.getLocation(), familySize, city, finStatus);
    	for (int i = 0; i < familySize; i++){
    		 	
	        //first pick sex
	        int sex;
	        if(migrationSim.random.nextBoolean())
	            sex = Constants.MALE;
	        else
	            sex = Constants.FEMALE;
	
	        //now get age
	        int age = pick_age(age_dist, city.getID());
	        //System.out.println("" + age);

	        //
	        Refugee refugee = new Refugee(sex, age, refugeeFamily);
	        refugeeFamily.getFamily().add(refugee);
    	}
    	
    	
    	//System.out.println(refugeeFamily);
    	return refugeeFamily;
    
    	
    	
    }
    
    private static int pick_age(HashMap<Integer, ArrayList<Double>> age_dist, int cityid)
    {
        //if(county_id == -9999)
           // county_id = Parameters.MIN_LIB_COUNTY_ID;
    	int category = 0;
    	double rand = migrationSim.random.nextDouble();
        ArrayList<Double> dist = age_dist.get(cityid);
        for (int i = 1; i < 4; i++){
        	if (rand >= dist.get(i-1) && rand <= dist.get(i)){
        		category = i;
                System.out.println("" + category);
        		break; //TODO DOES THIS ACTUALLY BREAK
        	}
        }

        switch (category) {
        case 0:  return migrationSim.random.nextInt(5); //0-4
        case 1:  return migrationSim.random.nextInt(13) + 5; //5-17
        case 2:  return migrationSim.random.nextInt(42) + 18; //18-59
        case 3:  return migrationSim.random.nextInt(41) + 60; //60+
        default: return 0;
        }

    }
    
    private static void setUpAgeDist(String age_dist_file)
    {
        try
        {
            // buffer reader for age distribution data
            CSVReader csvReader = new CSVReader(new FileReader(new File(age_dist_file)));
            csvReader.readLine();//skip the headers
            List<String> line = csvReader.readLine();
            while(!line.isEmpty())
            {
                //read in the county ids
                int city_id = NumberFormat.getNumberInstance(java.util.Locale.US).parse(line.get(0)).intValue();
                //relevant info is from 5 - 21
                ArrayList<Double> list = new ArrayList<Double>();
                double sum = 0;
                for(int i = 1; i <= 4; i++)
                {
                	double percentage = Double.parseDouble(line.get(i));
                	sum += percentage;
                    list.add(sum);
                }
                //System.out.println("sum = " + sum);
                //System.out.println();
                //now add it to the hashmap
                age_dist.put(city_id, list);

                line = csvReader.readLine();
            }
            System.out.println(age_dist);
        }
        catch(FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch(java.text.ParseException e)
        {
            e.printStackTrace();
        }
    }
 	
    private static double pick_fin_status() {
		// TODO Auto-generated method stub
		return 1.0;
	}
	private static int pickFamilySize() {
		int familySize = (int) Math.round(nd.sample());
		return familySize;
	}
    
	static void extractFromRoadLinks(GeomVectorField roadLinks, Migration migrationSim)
    {
        Bag geoms = roadLinks.getGeometries();
        Envelope e = roadLinks.getMBR();
        //Envelope e = migrationSim.regions.getMBR();
        double xmin = e.getMinX(), ymin = e.getMinY(), xmax = e.getMaxX(), ymax = e.getMaxY();
        int xcols = migrationSim.world_width - 1, ycols = migrationSim.world_height - 1;
        int count = 0;

        //allNetworks = new LinkedList<HashSet<LineString>>();
		//String[] roadAttributes = {"ID", "FR", "TO", "SPEED_1", "POP", "COST_1", "TLEVEL_1", "DEATHS_1","LENGTH_1"};
        // extract each edge
        for (Object o : geoms)
        {
            MasonGeometry gm = (MasonGeometry) o;
            int from = gm.getIntegerAttribute("FR");
            int to = gm.getIntegerAttribute("TO");
         	double speed = gm.getDoubleAttribute("SPEED_1");
         	double distance = gm.getDoubleAttribute("LENGTH_1");
         	double cost = gm.getDoubleAttribute("COST_1");
         	double transportlevel = gm.getDoubleAttribute("TLEVEL_1");
         	double deaths = gm.getDoubleAttribute("DEATHS_1");
         	
         	RoadInfo edgeinfo = new RoadInfo(gm.geometry,from, to, speed, distance, cost, transportlevel, deaths);
         	
         	// build road network
         	migrationSim.roadNetwork.addEdge(migrationSim.cityList.get(from) , migrationSim.cityList.get(to), edgeinfo);
         	migrationSim.roadNetwork.addEdge(migrationSim.cityList.get(to), migrationSim.cityList.get(from), edgeinfo);
         	
        /* 	
            if (gm.getGeometry() instanceof LineString)
            {
                count++;
                readLineString((LineString) gm.getGeometry(), xcols, ycols, xmin, ymin, xmax, ymax, migrationSim, edgeinfo);

            } else if (gm.getGeometry() instanceof MultiLineString)
            {
                MultiLineString mls = (MultiLineString) gm.getGeometry();
                for (int i = 0; i < mls.getNumGeometries(); i++)
                {
                    count++;
                    readLineString((LineString) mls.getGeometryN(i), xcols, ycols, xmin, ymin, xmax, ymax, migrationSim, edgeinfo);
                }
            }
            */
//            if(count%10000 == 0)
//                System.out.println("# of linestrings = " + count);

        }

    }
	
	/*
    static void readLineString(LineString geometry, int xcols, int ycols, double xmin,
            double ymin, double xmax, double ymax, Migration migrationSim, RoadInfo edgeinfo) {
    		
    		CoordinateSequence cs = geometry.getCoordinateSequence();

    			// iterate over each pair of coordinates and establish a link between
    			// them
    		
    		
    		 if(!allLineStrings.add(geometry)) //Uncomment for linestring trimming
    	            return;

    	        //linestring trimming: HashSet<LineString> curSet = new HashSet<LineString>();
    	        //curSet.add(geometry);
    	        //allNetworks.addFirst(curSet);
//    	        ListIterator<HashSet<LineString>> listIterator = allNetworks.listIterator();
//    	        listIterator.next();
//    	        int removeIndex = 0;
    		 Node oldNode = null; // used to keep track of the last node referenced
    	        Node oldNodeTrimmed = null; //used to keep track of last trimmed node referenced
    	        int trimmed_distance = 0;
    	        for (int i = 0; i < cs.size(); i++)
    	        {
    	            // calculate the location of the node in question
    	            double x = cs.getX(i), y = cs.getY(i);
    	            int xint = (int) Math.floor(xcols * (x - xmin) / (xmax - xmin)), yint = (int) (ycols - Math.floor(ycols * (y - ymin) / (ymax - ymin))); // REMEMBER TO FLIP THE Y VALUE

    	            if (xint >= migrationSim.world_width)
    	                continue;
    	            else if (yint >= migrationSim.world_height)
    	                continue;
    	         // find that node or establish it if it doesn't yet exist
    	            Bag ns = migrationSim.allRoadNodes.getObjectsAtLocation(xint, yint);
    	            Node n;
    	            if (ns == null)
    	            {
    	                n = new Node(new Int2D(xint, yint));
    	                n.lineStrings.add(geometry);
    	                n.index = i;
    	                migrationSim.allRoadNodes.setObjectLocation(n, xint, yint);
    	            }
    	            else //this means that we are connected to another linestring or this linestring
    	            {
    	                n = (Node) ns.get(0);
    	                
    	                //USE FOR NETWORK COLLAPSE
//    	                LineString searchFor = n.lineString;
//    	                ListIterator<HashSet<LineString>> nextIterator = allNetworks.listIterator();
//    	                //search for the other linestring
//    	                int temp = -1;
//    	                while(nextIterator.hasNext())
//    	                {
//    	                    HashSet<LineString> next = nextIterator.next();
//    	                    temp++;
//    	                    if(next.contains(searchFor))
//    	                    {
//    	                        if(next != curSet)
//    	                        {
//    	                            //add all from the previous hashset to this one
//    	                            next.addAll(curSet);
//    	                            curSet = next;
    	//
//    	                            //remove the earlier position
//    	                            //listIterator.remove();
//    	                            if(removeIndex != 0) {
//    	                                int john = 1;
//    	                                john++;
//    	                            }
//    	                            allNetworks.remove(removeIndex);
//    	                            if(removeIndex < temp)
//    	                                temp--;
//    	                            removeIndex = temp;
//    	                            //now reset the position of the iterator and change locations
//    	                            //removeIndex = nextIterator.nextIndex();
    	//
//    	                            if(removeIndex < 0 || !allNetworks.get(removeIndex).contains(geometry))
//    	                                System.out.println("ERROR ERROR ERROR ERROR!!!!!!!!!!!!!!!");
//    	                        }
//    	                        break;
//    	                    }
//    	                }
    	            }
    	            // attach the node to the previous node in the chain (or continue if
    	            // this is the first node in the chain of links)

    	            if (i == 0) { // can't connect previous link to anything
    	                oldNode = n; // save this node for reference in the next link
    	                continue;
    	            }

    	            //int weight = (int) n.location.distance(oldNode.location); // weight is just //TODO new weight
    	            // distance
    	            //add it to the thinned network if it is the first or last in the cs.

    	            if (oldNode == n) // don't link a node to itself
    	            {
    	                continue;
    	            }

    	            // create the new link and save it
    	            
    	            Edge e = new Edge(oldNode, n, edgeinfo);
    	            migrationSim.roadNetwork.addEdge(e);

    	            oldNode.links.add(e);
    	            n.links.add(e);
    	            n.weightOnLineString = trimmed_distance;
    	            oldNode = n; // save this node for reference in the next link
    	        }

    	        //if we haven't found any links the network should be null
    	  */
  	            
    
}


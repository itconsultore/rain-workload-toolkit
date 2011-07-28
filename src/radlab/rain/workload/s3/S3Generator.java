package radlab.rain.workload.s3;

import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import radlab.rain.Generator;
import radlab.rain.LoadProfile;
import radlab.rain.ObjectPool;
import radlab.rain.Operation;
import radlab.rain.ScenarioTrack;

public class S3Generator extends Generator 
{
	public static String CFG_USE_POOLING_KEY 		= "usePooling";
	public static String CFG_DEBUG_KEY		 		= "debug";
	public static String CFG_RNG_SEED_KEY	 		= "rngSeed";
	
	public static int GET 					= 0;
	public static int PUT 					= 1;
	public static int HEAD					= 2;
	public static int DELETE				= 3;
	public static int CREATE_BUCKET			= 4;
	public static int LIST_BUCKET			= 5;
	public static int DELETE_BUCKET			= 6;
	public static int LIST_ALL_BUCKETS		= 7;
	public static int RENAME				= 8;
	public static int MOVE					= 9;
	public static int MAX_OPERATIONS 		= 10;
		
	public static int DEFAULT_OBJECT_SIZE	= 4096;
	
	private boolean _usePooling					= true;
	@SuppressWarnings("unused")
	private boolean _debug 						= false;
	private Random _random						= null;
	private S3Transport _s3Client				= null;
	
	@SuppressWarnings("unused")
	private S3Request<String> _lastRequest 	= null;
	
	public S3Generator(ScenarioTrack track) 
	{
		super(track);
	}

	@Override
	public void dispose() {}

	@Override
	public long getCycleTime() 
	{
		return 0;
	}

	@Override
	public long getThinkTime() 
	{
		return 0;
	}

	@Override
	public void initialize() {}

	public void setUsePooling( boolean value ) { this._usePooling = value; }
	public boolean getUsePooling() { return this._usePooling; }
	
	public S3Transport getS3Transport() { return this._s3Client; }
	
	@Override
	public void configure( JSONObject config ) throws JSONException
	{
		if( config.has(CFG_USE_POOLING_KEY) )
			this._usePooling = config.getBoolean( CFG_USE_POOLING_KEY );
		
		if( config.has( CFG_DEBUG_KEY) )
			this._debug = config.getBoolean( CFG_DEBUG_KEY );
		
		// Look for a random number seed
		if( config.has( CFG_RNG_SEED_KEY ) )
			this._random = new Random( config.getLong(CFG_RNG_SEED_KEY) );
		else this._random = new Random();
		
		// Configure the s3 transport with the credentials we need to connect etc.
	}
	
	@Override
	public Operation nextRequest(int lastOperation) 
	{
		LoadProfile currentLoad = this.getTrack().getCurrentLoadProfile();
		this._latestLoadProfile = currentLoad;
		
		S3LoadProfile s3Profile = (S3LoadProfile) this._latestLoadProfile;
		
		S3Request<String> nextRequest = new S3Request<String>();
		// Pick the name of the object - this will involve choosing the folder names 
		
		
		double rndVal = this._random.nextDouble();
		int i = 0;
		
		// If we cared about access sequences we could check whether we just did a read or write
		// before picking the next operation
		for( i = 0; i < MAX_OPERATIONS; i++ )
		{
			if( rndVal <= s3Profile._opselect[i] )
				break;
		}
		nextRequest.op = i;
		
		// If we're writing then we need to set the size
		if( nextRequest.op == PUT )
		{
			// Use the size CDF to select the object size
			rndVal = this._random.nextDouble();
			int j = 0;
			for( j = 0; j < s3Profile._sizeMix.length; j++ )
			{
				if( rndVal <= s3Profile._sizeMix[j])
					break;
			}
			nextRequest.size = s3Profile._sizes[j];
		}
			
		// Update the last request
		this._lastRequest = nextRequest;
		return this.getS3Operation( nextRequest );
	}
	
	private S3Operation getS3Operation( S3Request<String> request )
	{
		if( request.op == GET )
			return this.createGetOperation( request );
		else if( request.op == PUT )
			return this.createPutOperation( request );
		else if( request.op == HEAD )
			return this.createHeadOperation( request );
		else if( request.op == DELETE )
			return this.createDeleteOperation( request );
		else if( request.op == CREATE_BUCKET )
			return this.createCreateBucketOperation( request );
		else if( request.op == LIST_BUCKET )
			return this.createListBucketOperation( request );
		else if( request.op == DELETE_BUCKET )
			return this.createDeleteBucketOperation( request );
		else if( request.op == LIST_ALL_BUCKETS )
			return this.createListAllBucketsOperation( request );
		else if( request.op == RENAME )
			return this.createRenameOperation( request );
		else if( request.op == MOVE )
			return this.createMoveOperation( request );
		else return null; 
	}
	
	public S3GetOperation createGetOperation( S3Request<String> request )
	{
		S3GetOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3GetOperation) pool.rentObject( S3GetOperation.NAME );	
		}
		
		if( op == null )
			op = new S3GetOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		
		op.prepare( this );
		return op;
	}
	
	public S3PutOperation createPutOperation( S3Request<String> request )
	{
		S3PutOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3PutOperation) pool.rentObject( S3PutOperation.NAME );	
		}
		
		if( op == null )
			op = new S3PutOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		
		// Check whether a value has been pre-set, if not then fill in random bytes
		if( request.value == null )
		{
			if( request.size < Integer.MAX_VALUE )
			{
				op._value = new byte[request.size];
			}
			else op._value = new byte[DEFAULT_OBJECT_SIZE];
			this._random.nextBytes( op._value );
		}
		else op._value = request.value;
		
		op.prepare( this );
		return op;
	}
	
	public S3HeadOperation createHeadOperation( S3Request<String> request )
	{
		S3HeadOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3HeadOperation) pool.rentObject( S3HeadOperation.NAME );	
		}
		
		if( op == null )
			op = new S3HeadOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		
		op.prepare( this );
		return op;
	}
	
	public S3DeleteOperation createDeleteOperation( S3Request<String> request )
	{
		S3DeleteOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3DeleteOperation) pool.rentObject( S3DeleteOperation.NAME );	
		}
		
		if( op == null )
			op = new S3DeleteOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		
		op.prepare( this );
		return op;
	}
	
	public S3CreateBucketOperation createCreateBucketOperation( S3Request<String> request )
	{
		S3CreateBucketOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3CreateBucketOperation) pool.rentObject( S3CreateBucketOperation.NAME );	
		}
		
		if( op == null )
			op = new S3CreateBucketOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		
		op.prepare( this );
		return op;
	}
	
	public S3ListBucketOperation createListBucketOperation( S3Request<String> request )
	{
		S3ListBucketOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3ListBucketOperation) pool.rentObject( S3ListBucketOperation.NAME );	
		}
		
		if( op == null )
			op = new S3ListBucketOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		
		op.prepare( this );
		return op;
	}
	
	public S3DeleteBucketOperation createDeleteBucketOperation( S3Request<String> request )
	{
		S3DeleteBucketOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3DeleteBucketOperation) pool.rentObject( S3DeleteBucketOperation.NAME );	
		}
		
		if( op == null )
			op = new S3DeleteBucketOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		
		op.prepare( this );
		return op;
	}
	
	public S3ListAllBucketsOperation createListAllBucketsOperation( S3Request<String> request )
	{
		S3ListAllBucketsOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3ListAllBucketsOperation) pool.rentObject( S3ListAllBucketsOperation.NAME );	
		}
		
		if( op == null )
			op = new S3ListAllBucketsOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// All we need are the AWS credentials to query S3
		
		op.prepare( this );
		return op;
	}
	
	public S3MoveOperation createMoveOperation( S3Request<String> request )
	{
		S3MoveOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3MoveOperation) pool.rentObject( S3MoveOperation.NAME );	
		}
		
		if( op == null )
			op = new S3MoveOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		op._newBucket = request.newBucket;
		
		op.prepare( this );
		return op;
	}
	
	public S3RenameOperation createRenameOperation( S3Request<String> request )
	{
		S3RenameOperation op = null;
		
		if( this._usePooling )
		{
			ObjectPool pool = this.getTrack().getObjectPool();
			op = (S3RenameOperation) pool.rentObject( S3RenameOperation.NAME );	
		}
		
		if( op == null )
			op = new S3RenameOperation( this.getTrack().getInteractive(), this.getScoreboard() );
		
		// Set the specific fields
		op._key = request.key;
		op._newKey = request.newKey;
		
		op.prepare( this );
		return op;
	}
}
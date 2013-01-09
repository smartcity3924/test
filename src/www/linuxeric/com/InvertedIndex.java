package www.linuxeric.com;

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;


public class InvertedIndex {

	public static class InvertedIndexMapper extends
			Mapper<Object, Text, Text, Text>{
		
		private Text keyInfo = new Text();
		private Text valueInfo = new Text();
		private FileSplit split;
		
		public void map(Object key, Text value, Context context) 
				throws IOException, InterruptedException{
			
			split = (FileSplit)context.getInputSplit();
			
			StringTokenizer itr = new StringTokenizer(value.toString());
			System.out.println(split.toString());
			
			while(itr.hasMoreTokens()){
				keyInfo.set(itr.nextToken() + ":" + split.getPath().toString());
				valueInfo.set("1");
				context.write(keyInfo, valueInfo);
			}
		}
	}
	
	public static class InvertedIndexCombiner extends 
			Reducer<Text, Text, Text, Text>{
		
		private Text info= new Text();
		
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException{
			
			int sum = 0;
			for(Text value : values){
				sum += Integer.parseInt(value.toString());
			}
			
			int splitIndex = key.toString().indexOf(":");
			info.set(key.toString().substring(splitIndex + 1) + ":" + sum);
			key.set(key.toString().substring(0, splitIndex));
			context.write(key, info);
		}
	}
	
	public static class InvertedIndexReducer extends
			Reducer<Text, Text, Text, Text>{
		
		private Text result = new Text();
		
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException{
			
			
			String fileList = new String();
			for(Text value : values){
				fileList += value.toString() + ";";
			}
			result.set(fileList);
			
			context.write(key, result);
		}
	}
		
	
	public static void main(String[] args) throws Exception{
		
		Configuration conf = new Configuration();
		String[] arguments=new String[]{"input","output"};
		
		FileSystem fs=FileSystem.get(conf);
		DistributedFileSystem hdfs=(DistributedFileSystem)fs;
		Path path=new Path(arguments[1]);
		boolean isexits=hdfs.exists(path);
		if(isexits){
			boolean isdeleted=hdfs.delete(path,true);
			if(isdeleted){
				System.out.println(path+" already exits, and it is deleted successfully.");
			}
			else {
				System.out.println(path+" already exits, but it can not be deleted.\n The system will exit.");
				System.exit(1);
			}
		}
		
		String[] otherArgs = new GenericOptionsParser(conf, arguments).getRemainingArgs();		
		if(otherArgs.length != 2){
			System.err.println("Usage: invertedindex <in> <out>");
		    System.exit(2);
		}
		
		Job job = new Job(conf, "InvertedIndex");
		job.setJarByClass(InvertedIndex.class);
		
		job.setMapperClass(InvertedIndexMapper.class);
		
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		
		job.setCombinerClass(InvertedIndexCombiner.class);
		
		job.setReducerClass(InvertedIndexReducer.class);
		
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		
		FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
		FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
	
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}

}


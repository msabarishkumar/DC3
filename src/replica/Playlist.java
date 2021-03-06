package replica;

import java.util.Hashtable;

public class Playlist {
	Hashtable<String, String> playList = new Hashtable<String, String>();

	public Playlist() {
	}
	
	public void performOperation(Operation op){
		switch (op.type) {
			case PUT:
				add(op.song, op.url);
				break;
			case DELETE:
				delete(op.song);
				break;
			default: break;
		}
	}
	
	public synchronized void add(String song, String url) {
		this.playList.put(song, url);
	}
	
	/*public synchronized void delete(String song) throws SongNotFoundException {
		if (this.playList.containsKey(song)) {
			this.playList.remove(song);
			return;
		}
		
		throw new SongNotFoundException("Could not find song: " + song);
	} */
	
	public synchronized void delete(String song){
		this.playList.remove(song);
	}
	
	public synchronized void edit(String song, String newUrl) throws SongNotFoundException {
		if (this.playList.containsKey(song)) {
			this.playList.put(song, newUrl);
			return;
		}
		
		throw new SongNotFoundException("Could not find song: " + song);
	}
	
	public synchronized String read(String song){
		if(this.playList.containsKey(song)){
			return this.playList.get(song);
		}
		return "ERR_KEY";
	}
	
	public String toString() {
		StringBuilder builder = new StringBuilder();
		//builder.append("Playlist: \n");
		for (String key: playList.keySet()) {
			builder.append(key + " : " + playList.get(key));
			builder.append("\n");
		}
		builder.append("-END \n");
		return builder.toString();
	}
	
	public void clear() {
		playList = new Hashtable<String, String>();
	}
	
	public Playlist clone(){
		Playlist clone = new Playlist();
		//clone.playList = (Hashtable<String, String>) this.playList.clone();
		clone.playList = new Hashtable<String, String>(this.playList);
		return clone;
	}
}

class SongNotFoundException extends Exception {
	private static final long serialVersionUID = 1L;
	public SongNotFoundException(String ex) {
		super(ex);
	}
}

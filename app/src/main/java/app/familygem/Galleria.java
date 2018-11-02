// Lista dei Media

package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import app.familygem.dettaglio.Immagine;
import static app.familygem.Globale.gc;

public class Galleria extends Fragment {
	
	@Override
	public void onCreate( Bundle stato ) {
		super.onCreate( stato );
	}

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle stato ) {
		setHasOptionsMenu(true);
		View vista = inflater.inflate( R.layout.magazzino, container, false);
		LinearLayout scatola = vista.findViewById( R.id.magazzino_scatola );
		VisitaListaMedia visitaMedia = new VisitaListaMedia( !getActivity().getIntent().getBooleanExtra("galleriaScegliMedia",false ) );
		gc.accept( visitaMedia );
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle( visitaMedia.listaMedia.size() + " " + getString(R.string.media).toLowerCase() );
		for( Map.Entry<Media,Object> dato : visitaMedia.listaMedia.entrySet() )
			poniMedia( scatola, dato.getValue(), dato.getKey(), true );
		return vista;
	}

	// Lista dei media ciascuno col suo oggetto contenitore
	// Serve per i comanti Scollega e Elimina nel menu contestuale di ciascun media
	@Deprecated
	static Map<Media,Object> elencaMedia() {
		Map<Media,Object> lista = new HashMap<>();
		for( Media m : Globale.gc.getMedia() )    // media record in tutto il gedcom
			lista.put( m, Globale.gc );
		for( Person p : gc.getPeople() ) {    // local media negli individui
			aggiungiDati( lista, p );
			for( SourceCitation c : p.getSourceCitations() )    // local media nelle citazioni fonti delle persone
				aggiungiDati( lista, c );
		}
		for( Source s : gc.getSources() ) {    // local media nelle fonti
			aggiungiDati( lista, s );
		}
		for( Family f : gc.getFamilies() ) {    // local media nelle famiglie
			aggiungiDati( lista, f );
			for( SourceCitation c : f.getSourceCitations() )    // local media nelle citazioni fonti delle famiglie
				aggiungiDati( lista, c );
		}
		return lista;
	}
	private static void aggiungiDati( Map<Media,Object> lista, Object contenitore ) {
		for( Media med : ((MediaContainer)contenitore).getMedia() )
			lista.put( med, contenitore );
	}

	// Inserisce un singolo elemento mediale di elenco
	public static void poniMedia( final LinearLayout scatola, final Object contenitore, final Media med, boolean dettagli ) {
		View vistaMedia = LayoutInflater.from(scatola.getContext()).inflate( R.layout.galleria_pezzo, scatola, false );
		scatola.addView( vistaMedia );
		if( med != null ) {
			ImageView vistaFoto = vistaMedia.findViewById( R.id.galleria_foto );
			U.mostraMedia( vistaFoto, med );
			String testo = "";
			if( med.getTitle() != null )
				testo = med.getTitle() + "\n";
			if( med.getFile() != null )
				testo += med.getFile();
			else
				testo += U.percorsoMedia( med );
			( (TextView) vistaMedia.findViewById( R.id.galleria_testo ) ).setText( testo );
			if( dettagli ) {
				vistaMedia.setTag( R.id.tag_oggetto, med );
				vistaMedia.setTag( R.id.tag_contenitore, contenitore );
				final AppCompatActivity attiva = (AppCompatActivity) scatola.getContext();
				if( scatola.getContext() instanceof Individuo ) { // Fragment individuoMedia
					attiva.getSupportFragmentManager()
							.findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":0" )    // non garantito in futuro
							.registerForContextMenu( vistaMedia );
				} else if( scatola.getContext() instanceof Principe ) // Fragment Galleria
					attiva.getSupportFragmentManager().findFragmentById( R.id.contenitore_fragment ).registerForContextMenu( vistaMedia );
				else    // nelle AppCompatActivity
					attiva.registerForContextMenu( vistaMedia );
				vistaMedia.setOnClickListener( new View.OnClickListener() {
					public void onClick( View vista ) {
						// Galleria in modalità scelta dell'oggetto media
						// Restituisce l'id di un oggetto media a IndividuoMedia
						if( attiva.getIntent().getBooleanExtra( "galleriaScegliMedia", false ) ) {
							Intent intent = new Intent();
							intent.putExtra( "idMedia", med.getId() );
							attiva.setResult( AppCompatActivity.RESULT_OK, intent );
							attiva.finish();
						// Galleria in modalità normale Apre Immagine
						} else {
							Ponte.manda( med, "oggetto" );
							Ponte.manda( contenitore, "contenitore" );
							scatola.getContext().startActivity( new Intent( scatola.getContext(), Immagine.class ) );
						}
					}
				} );
			} else {    // Media inerte
				vistaMedia.setBackground( null );
				// todo icona più piccola? solo titolo o nome file?
			}
		}
	}

	// ToDo: probabilmente rimpiazzabile da un Visitor
	public static int popolarita( Media med ) {
		int quante = 0;
		for( Media m : gc.getMedia() ) {	// media record in tutto il gedcom
			if( m.equals(med) )
				quante++;
		}
		for( Person p : gc.getPeople() ) {	// local media negli individui
			for( Media m : p.getMedia() )
				if( m.equals(med) )
					quante++;
		}
		for( Source f : gc.getSources() ) {	// local media nelle fonti
			for( Media m : f.getMedia() )
				if( m.equals(med) )
					quante++;
		}
		for( Family f : gc.getFamilies() ) {	// local media nelle famiglie
			for( Media m : f.getMedia() )
				if( m.equals(med) )
					quante++;
		}
		return quante;
	}

	public static void nuovoMedia( Context contesto, Object contenitore ){
		Media media = new Media();
		int val, max = 0;
		for( Media med : gc.getMedia() ) {
			val = Anagrafe.idNumerico( med.getId() );
			if( val > max )	max = val;
		}
		media.setId( "M" + (max+1) );
		media.setFile( "" );
		gc.addMedia( media );
		if( contenitore != null ) {
			MediaRef rifMed = new MediaRef();
			rifMed.setRef( media.getId() );
			((MediaContainer)contenitore).addMediaRef( rifMed );
			Ponte.manda( contenitore, "contenitore" );
		}
		Ponte.manda( media, "oggetto" );
		contesto.startActivity( new Intent( contesto, Immagine.class ) );
		// todo? Dettaglio.edita( text File );
	}

	public static void scollegaMedia( Media media, Object contenitore, View vista ) {
		List<MediaRef> lista = ((MediaContainer)contenitore).getMediaRefs();
		for( MediaRef ref : lista ) {
			s.l( ref.getRef() +"    "+ ref.getMedia( Globale.gc ));
			if( ref.getMedia( Globale.gc ) == null ) {	// Eventuale ref a un media inesistente
				lista.remove( ref );	// rimuove il ref e ricomincia
				scollegaMedia( media, contenitore, vista );
				return;
			}
			if( ref.getMedia( Globale.gc ).equals( media ) ) {
				lista.remove( ref );
				scollegaMedia( media, contenitore, vista );
				return;
			}
		}
		((MediaContainer)contenitore).setMediaRefs( lista );	// ok!
		vista.setVisibility( View.GONE );
	}

	public static void eliminaMedia( Media media, Object contenitore, View vista ) {
		// Solo per il particolare modo in cui sono collezionati i media in Galleria
		// qui vengono eliminati i media di Gedcom (che non extende MediaContainer)
		if( media.getId() != null ) {	// media OBJECT
			gc.getMedia().remove( media );	// ok
			gc.createIndexes();	// necessario per farlo scomparire anche dall'oggetto contenitore
		} else	// media LOCALI
			((MediaContainer)contenitore).getMedia().remove( media );
		vista.setVisibility( View.GONE );
	}

	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {
		menu.add( 0,0,0, R.string.new_m );
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch( item.getItemId() ) {
			case 0:
				Galleria.nuovoMedia( getContext(), null);
				break;
			default:
				return false;
		}
		return true;
	}

	// Menu contestuale
	View vistaFoto;
	Media med;
	Object contenit;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaFoto = vista;
		med = (Media) vistaFoto.getTag( R.id.tag_oggetto );
		contenit = vistaFoto.getTag( R.id.tag_contenitore );
		menu.add(0, 0, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		if( item.getItemId() == 0 ) {
			eliminaMedia( med, contenit, vistaFoto );
			return true;
		}
		return false;
	}

}
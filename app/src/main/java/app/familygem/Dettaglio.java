package app.familygem;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.model.Submitter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import app.familygem.dettaglio.CitazioneFonte;
import app.familygem.dettaglio.Estensione;
import app.familygem.dettaglio.Evento;
import app.familygem.dettaglio.Famiglia;
import app.familygem.dettaglio.Indirizzo;
import app.familygem.dettaglio.Nota;
import static app.familygem.Globale.gc;

public class Dettaglio extends AppCompatActivity {

	public LinearLayout box;
	public Object oggetto;	// Name Media SourceCitation ecc.
	public TextView vistaId;
	public String occorrenze = "";
	public Object contenitore = Ponte.ricevi("contenitore");	// in particolare per poter eliminare l'oggetto
	List<Uovo> ovi = new ArrayList<>();	// Lista dei pezzi editabili
	TreeMap<String,String> eventiVari; // Eventi per il FAB di Famiglia
	public Person unRappresentanteDellaFamiglia; // una Person della Famiglia per nascondere nel FAB 'Collega persona'
	EditoreData editoreData;

	@Override
	protected void onCreate( Bundle stato ) {
		super.onCreate( stato );
		setContentView( R.layout.dettaglio );
		box = findViewById( R.id.dettaglio_scatola );
		vistaId = findViewById( R.id.dettaglio_id );

		impagina();

		if( oggetto == null)
			onBackPressed(); // salta tutti gli altri dettagli senza oggetto

		TextView vistaOccorrenze = findViewById( R.id.dettaglio_citazioni );
		if( occorrenze.isEmpty() )
			vistaOccorrenze.setVisibility( View.GONE );
		else
			vistaOccorrenze.setText( occorrenze );

		findViewById( R.id.dettaglio_fab ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View vista ) {
				// Menu del FAB: solo coi metodi che non sono già presenti in box
				PopupMenu popup = new PopupMenu( Dettaglio.this, vista );
				Menu menu = popup.getMenu();
				String[] conIndirizzo = { "Www", "Email", "Phone", "Fax" }; // questi oggetti compaiono nel FAB di Evento se esiste un Indirizzo
				int u = 0;
				for( Uovo uovo : ovi ) {
					boolean giaMesso = false;
					boolean indirizzoPresente = false;
					for( int i = 0; i < box.getChildCount(); i++ ) {
						Object ogg = box.getChildAt(i).getTag(R.id.tag_oggetto);
						if( ogg != null && ogg.equals(uovo.oggetto) )
							giaMesso = true;
						if( ogg instanceof Address )
							indirizzoPresente = true;
					}
					if( !giaMesso ) {
						if( uovo.comune || ( indirizzoPresente && Arrays.asList(conIndirizzo).contains(uovo.oggetto) ) )
							menu.add( 0, u, 0, uovo.titolo );
					}
					u++;
				}
				if( oggetto instanceof Family ) {
					SubMenu subNuovi = menu.addSubMenu( 0, 100, 0, R.string.new_relative );
					subNuovi.add( 0, 110, 0, R.string.spouse );	// todo? nascondere Coniuge se ci sono già nella famiglia?
						// todo? o magari mettere avviso "Questa famiglia ha già i genitori... Vuoi forse creare un altro matrimonio per uno dei genitori?"
					subNuovi.add( 0, 111, 0, R.string.child );
					if( U.ciSonoIndividuiCollegabili( unRappresentanteDellaFamiglia ) ) {
						SubMenu subCollega = menu.addSubMenu( 0, 100, 0, R.string.link_person );
						subCollega.add( 0, 112, 0, R.string.spouse );
						subCollega.add( 0, 113, 0, R.string.child );
					}
					SubMenu subEvento = menu.addSubMenu( 0, 100, 0, R.string.event );
					subEvento.add( 0, 114, 0, R.string.marriage );
					// Crea la lista degli altri eventi che si possono inserire
					Set<String> eventiIndividuo = EventFact.PERSONAL_EVENT_FACT_TAGS;
					for( String tag : EventFact.FAMILY_EVENT_FACT_TAGS )
						eventiIndividuo.remove( tag );
					eventiVari = new TreeMap<>( EventFact.DISPLAY_TYPE );
					for( String tag : eventiIndividuo )
						eventiVari.remove( tag );
					Iterator<Map.Entry<String,String>> eventi = eventiVari.entrySet().iterator();
					while( eventi.hasNext() ) { // Rimuove i tag lunghi e _speciali
						Map.Entry<String,String> ev = eventi.next();
						if( ev.getKey().length() > 4 || ev.getKey().startsWith( "_" ) )
							eventi.remove();
					}
					SubMenu subAltri = subEvento.addSubMenu( 0, 100, 0, R.string.other );
					int i = 0;
					for( TreeMap.Entry<String,String> event : eventiVari.entrySet() ) {
						subAltri.add( 0, 200+i, 0, event.getValue() + " - " + event.getKey() );
						i++;
					}
				}
				if( oggetto instanceof Source && findViewById(R.id.citazione_fonte) == null ) { // todo dubbio: non dovrebbe essere citazione_ARCHIVIO ?
					SubMenu subArchivio = menu.addSubMenu( 0, 100, 0, R.string.repository );
					subArchivio.add( 0, 101, 0, R.string.new_repository );
					subArchivio.add( 0, 102, 0, R.string.link_repository );
				}
				if( oggetto instanceof NoteContainer ) {
					SubMenu subNota = menu.addSubMenu( 0, 100, 0, R.string.note );
					subNota.add( 0, 103, 0, R.string.new_note );
					subNota.add( 0, 104, 0, R.string.new_shared_note );
					subNota.add( 0, 105, 0, R.string.link_shared_note );
				}
				if( oggetto instanceof SourceCitationContainer || oggetto instanceof Note ) {
					SubMenu subFonte = menu.addSubMenu( 0, 100, 0, R.string.source );
					subFonte.add( 0, 106, 0, R.string.new_source_note );
					subFonte.add( 0, 107, 0, R.string.new_source );
					subFonte.add( 0, 108, 0, R.string.link_source );
				}
				// TODO: Submenu Media
				popup.show();
				popup.setOnMenuItemClickListener( new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick( MenuItem item ) {
						// FAB + mette un nuovo uovo e lo rende subito editabile
						int id = item.getItemId();
						if( id < 100 ) {
							Object coso = ovi.get( id ).oggetto;
							if( coso instanceof Address ) {    // coso è un new Address()
								if( oggetto instanceof EventFact )
									((EventFact)oggetto).setAddress( (Address)coso );
								else if( oggetto instanceof Submitter )
									((Submitter)oggetto).setAddress( (Address)coso );
							}
							View pezzo = creaPezzo( ovi.get(id).titolo, "", coso, ovi.get(id).multiLinea );
							if( coso instanceof String )
								edita( pezzo );
							// todo : aprire Address nuovo per editarlo
						} else if( id == 101 ) {
							Magazzino.nuovoArchivio( Dettaglio.this, (Source)oggetto );
						} else if( id == 102 ) {
							Intent intento = new Intent( Dettaglio.this, Principe.class );
							intento.putExtra( "magazzinoScegliArchivio", true );
							startActivityForResult( intento,4562 );
						} else if( id == 103 ) { // Nuova nota
							Note nota = new Note();
							nota.setValue( "" );
							((NoteContainer)oggetto).addNote( nota );
							Ponte.manda( nota, "oggetto" );
							Ponte.manda( oggetto, "contenitore" );
							startActivity( new Intent( Dettaglio.this, Nota.class ) );
						} else if( id == 104 ) { // Nuova nota condivisa
							Quaderno.nuovaNota( Dettaglio.this, oggetto );
						} else if( id == 105 ) { // Collega nota condivisa
							Intent intento = new Intent( Dettaglio.this, Principe.class );
							intento.putExtra( "quadernoScegliNota", true );
							startActivityForResult( intento,7074 );
						} else if( id == 106 ) { // Nuova fonte-nota
							SourceCitation citaz = new SourceCitation();
							citaz.setValue( "" );
							if( oggetto instanceof Note ) ((Note)oggetto).addSourceCitation( citaz );
							else ((SourceCitationContainer)oggetto).addSourceCitation( citaz );
							Ponte.manda( citaz, "oggetto" );
							Ponte.manda( oggetto, "contenitore" );
							startActivity( new Intent( Dettaglio.this, CitazioneFonte.class ) );
						} else if( id == 107 ) {  // Nuova fonte
							Biblioteca.nuovaFonte( Dettaglio.this, oggetto );
						} else if( id == 108 ) { // Collega fonte
  							Intent intent = new Intent( Dettaglio.this, Principe.class );
							intent.putExtra( "bibliotecaScegliFonte", true );
							startActivityForResult( intent, 5065 );
						} else if( id == 110 || id == 111 ) { // Nuovo familiare
							Intent intento = new Intent( Dettaglio.this, EditaIndividuo.class );
							intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
							intento.putExtra( "idFamiglia", ((Family)oggetto).getId() );
							intento.putExtra( "relazione", id - 109);
							startActivity( intento );
						} else if( id == 112 || id == 113 ) { // Nuovo familiare
							Intent intento = new Intent( Dettaglio.this, Principe.class );
							intento.putExtra( "anagrafeScegliParente", true );
							intento.putExtra( "relazione", id - 111 );
							startActivityForResult( intento,34417 );
						} else if( id == 114 ) { // Metti matrimonio
							EventFact nuovoEvento = new EventFact();
							nuovoEvento.setTag( "MARR" );
							nuovoEvento.setDate( "" );
							nuovoEvento.setPlace( "" );
							((Family)oggetto).addEventFact( nuovoEvento );
							Ponte.manda( nuovoEvento, "oggetto" );
							Ponte.manda( oggetto, "contenitore" );
							startActivity( new Intent( Dettaglio.this, Evento.class ) );
						} else if( id >= 200 ) { // Metti altro evento
							EventFact nuovoEvento = new EventFact();
							nuovoEvento.setTag( eventiVari.keySet().toArray( new String[eventiVari.size()] )[id - 200] );
							((Family)oggetto).addEventFact( nuovoEvento );
							creaPezzo( nuovoEvento.getDisplayType(), "", nuovoEvento, false );
						}
						return true;
					}
				});
			}
		});
	}

	// Imposta ciò che è stato scelto nelle liste
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( resultCode == RESULT_OK ) {
			// Dal submenu 'Collega...' in FAB
			if( requestCode == 34417 ) { // Familiare scelto in Anagrafe
				Famiglia.aggrega( gc.getPerson(data.getStringExtra("idParente")), (Family)oggetto, data.getIntExtra("relazione",0) );
			} else if( requestCode == 5065 ) { // Fonte scelta in Biblioteca
				SourceCitation citaFonte = new SourceCitation();
				citaFonte.setRef( data.getStringExtra("idFonte") );
				if( oggetto instanceof Note ) ((Note)oggetto).addSourceCitation( citaFonte );
				else ((SourceCitationContainer)oggetto).addSourceCitation( citaFonte );
			} else if( requestCode == 7074 ) {  // Nota condivisa
				NoteRef rifNota = new NoteRef();
				rifNota.setRef( data.getStringExtra( "idNota" ) );
				( (NoteContainer) oggetto ).addNoteRef( rifNota );
			} else if( requestCode == 4562  ) { // Archivio scelto in Magazzino da Fonte
				RepositoryRef archRef = new RepositoryRef();
				archRef.setRef( data.getStringExtra("idArchivio") );
				((Source)oggetto).setRepositoryRef( archRef );
			} else if( requestCode == 5173 ) { // Importa un file scelto col file manager da Immagine
				try {
					Uri uri = data.getData();
					String percorso = U.uriPercorsoFile( uri );
					File fileMedia;
					if( percorso.lastIndexOf( '/' ) > 0 ) {    // se è un percorso completo del file
						// Apre direttamente il file
						fileMedia = new File( percorso );
						if( fileMedia.exists() ) {
							// TODO questo è sciocco perché probabilmente l'utente non vuole modificare i percorsi di tutti gli altri file...
							// TODO, solo se "non li trova"
							Globale.preferenze.alberoAperto().cartella = fileMedia.getParent();
							Globale.preferenze.salva();
						}
					} else {    // è solo il nome del file 'pippo.png'
						// Copia il file (che può essere di qualsiasi tipo) nella memoria esterna della app
						// /mnt/shell/emulated/0/Android/data/lab.gedcomy/files/
						InputStream input = getContentResolver().openInputStream( uri );
						String percorsoMemoria = getExternalFilesDir(null) + "/" + Globale.preferenze.idAprendo;
						File dirMemoria = new File( percorsoMemoria );
						if( !dirMemoria.exists() )
							dirMemoria.mkdir();
						// Todo: controllare che non esista già il nome del file in percorsoCartella, quindi rinominarlo con +(1)
						fileMedia = new File( percorsoMemoria, percorso );
						FileUtils.copyInputStreamToFile( input, fileMedia );

					}
					((Media)oggetto).setFile( fileMedia.getAbsolutePath() );
				} catch( IOException e ) {
					Toast.makeText( Dettaglio.this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
					return;
				}
			}
			//  da menu contestuale 'Scegli...'
			if( requestCode == 5390  ) { // Imposta l'archivio che è stato scelto in Magazzino da ArchivioRef
				((RepositoryRef)oggetto).setRef( data.getStringExtra("idArchivio") );
			} else if( requestCode == 7047  ) { // Imposta la fonte che è stata scelta in Biblioteca da CitazioneFonte
				((SourceCitation)oggetto).setRef( data.getStringExtra("idFonte") );
			}
			U.salvaJson();
			Globale.editato = true; // indica di ricaricare sia questo Dettaglio grazie al seguente onRestart(), sia Individuo o Famiglia
			Ponte.manda( oggetto, "oggetto" ); // li spedisce per riprenderli subito dopo nel onRestart()
			if( contenitore != null )
				Ponte.manda( contenitore, "contenitore" );
		}
	}

	// Aggiorna i contenuti quando si torna indietro con backPressed()
	@Override
	public void onRestart() {
		super.onRestart();
		s.l( "onRestart di Dettaglio " + Globale.editato );
		if( Globale.editato ) { // rinfresca il dettaglio
			//Globale.editato = false;
			recreate();
		}
	}

	public void impagina() {}

	class Uovo {
		String titolo;
		Object oggetto;
		boolean comune; // indica se farlo comparire nel menu del FAB per inserire il pezzo
		boolean multiLinea;
		Uovo( String titolo, Object oggetto, boolean comune, boolean multiLinea ) {
			this.titolo = titolo;
			this.oggetto = oggetto;
			this.comune = comune;
			this.multiLinea = multiLinea;
			ovi.add( this );
		}
	}

	// Il metodo base per i pezzi mono-linea
	public void metti( String titolo, String metodo ) {
		metti( titolo, metodo, true, false );
	}

	public void metti( String titolo, String metodo, boolean comune, boolean multiLinea ) {
		new Uovo( titolo, metodo, comune, multiLinea );
		String testo;
		try {
			testo = (String) oggetto.getClass().getMethod( "get" + metodo ).invoke( oggetto );
		} catch( IllegalAccessException|InvocationTargetException|NoSuchMethodException|SecurityException e ) {
			testo = "ERROR: " + e.getMessage();
		}
		if( !( oggetto instanceof EventFact && metodo.equals("Value") && testo!=null && testo.equals("Y") ) )
			creaPezzo( titolo, testo, metodo, multiLinea );
	}

	// diverse firme per intercettare i vari tipi di oggetto
	public void metti( String titolo, Address indirizzo ) {
		Address indirizzoNonNullo = indirizzo==null ? new Address() : indirizzo;
		new Uovo( titolo, indirizzoNonNullo, true, false );
		creaPezzo( titolo, indirizzo(indirizzo), indirizzoNonNullo, false );
	}

	public void metti( String titolo, EventFact evento ) {
		EventFact eventoNonNullo = evento==null ? new EventFact() : evento;
		new Uovo( titolo, eventoNonNullo, true, false );
		creaPezzo( titolo, evento(evento), eventoNonNullo, false );
	}

	public View creaPezzo( String titolo, final String testo, final Object coso, boolean multiLinea ) {
		if( testo == null ) return null;
		View vistaPezzo = LayoutInflater.from(box.getContext()).inflate( R.layout.pezzo_fatto, box, false );
		box.addView( vistaPezzo );
		((TextView)vistaPezzo.findViewById( R.id.fatto_titolo )).setText( titolo );
		((TextView)vistaPezzo.findViewById( R.id.fatto_testo )).setText( testo );
		final EditText vistaEditabile = vistaPezzo.findViewById( R.id.fatto_edita );
		vistaEditabile.setText( testo ); // ok nella maggior parte dei casi, ma il testo mostrato in vistaEditabile non si aggiorna dopo onActivityResult()
		vistaEditabile.post( new Runnable() {
			@Override
			public void run() {
				vistaEditabile.setText( testo ); // un po' ridondante, ma aggiorna il testo in vistaEditabile dopo onActivityResult()
			}
		});
		if( multiLinea ) {
			vistaEditabile.setInputType( InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE );
			vistaEditabile.setVerticalScrollBarEnabled( true );
		}
		View.OnClickListener clicco = null;
		if( coso instanceof String ) {	// Metodo
			clicco = new View.OnClickListener() {
				public void onClick( View vista ) {
					edita( vista );
				}
			};
			// Se si tratta di una data
			if( coso.equals("Date") ) {
				editoreData = vistaPezzo.findViewById( R.id.fatto_data );
				editoreData.inizia( vistaEditabile );
			}
		} else if( coso instanceof Address ) {	// Indirizzo
			clicco = new View.OnClickListener() {
				public void onClick( View vista ) {
					Ponte.manda( coso, "oggetto" );
					Ponte.manda( oggetto, "contenitore" );
					startActivity( new Intent( Dettaglio.this, Indirizzo.class ) );
				}
			};
		} else if( coso instanceof EventFact ) {	// Evento
			clicco = new View.OnClickListener() {
				public void onClick( View vista ) {
					Ponte.manda( coso, "oggetto" );
					Ponte.manda( oggetto, "contenitore" );
					startActivity( new Intent( Dettaglio.this, Evento.class ) );
				}
			};
			// Gli EventFact (in particolare in Famiglia) possono avere delle note
			LinearLayout scatolaNote = vistaPezzo.findViewById( R.id.fatto_note );
			U.mettiNote( scatolaNote, coso, false );
		} else if( coso instanceof GedcomTag ) {	// Estensione
			clicco = new View.OnClickListener() {
				public void onClick( View vista ) {
					Ponte.manda( coso, "oggetto" );
					Ponte.manda( oggetto, "contenitore" );
					startActivity( new Intent( Dettaglio.this, Estensione.class ) );
				}
			};
		}
		vistaPezzo.setOnClickListener( clicco );
		registerForContextMenu( vistaPezzo );
		vistaPezzo.setTag( R.id.tag_oggetto, coso ); // Serve a vari processi per riconoscere il pezzo
		return vistaPezzo;
	}

	public void mettiEstensioni( ExtensionContainer contenitore ) {
		for( app.familygem.Estensione est : U.trovaEstensioni( contenitore ) ) {
			creaPezzo( est.nome, est.testo, est.gedcomTag, false );
		}
	}

	public static String indirizzo( Address ind ) {
		if( ind == null ) return null;
		String txt = "";
		if( ind.getValue() != null )
			txt = ind.getValue() + "\n";
		if( ind.getAddressLine1() != null )
			txt += ind.getAddressLine1() + "\n";
		if( ind.getAddressLine2() != null )
			txt += ind.getAddressLine2() + "\n";
		if( ind.getAddressLine3() != null )
			txt += ind.getAddressLine3() + "\n";
		if( ind.getPostalCode() != null ) txt += ind.getPostalCode() + " ";
		if( ind.getCity() != null ) txt += ind.getCity() + " ";
		if( ind.getState() != null ) txt += ind.getState();
		if( ind.getPostalCode()!=null || ind.getCity()!=null || ind.getState()!=null )
			txt += "\n";
		if( ind.getCountry() != null )
			txt += ind.getCountry();
		if( txt.endsWith("\n") )
			txt = txt.substring( 0, txt.length() - 1 ).trim();
		return txt;
	}

	public static String evento( EventFact ef ) {
		if( ef == null ) return null;
		String txt = "";
		if( ef.getValue() != null )
			txt = ef.getValue() + "\n";
		if( ef.getDate() != null )
			txt += ef.getDate() + "\n";
		if( ef.getPlace() != null )
			txt += ef.getPlace();
		if( txt.endsWith( "\n" ) )
			txt = txt.substring( 0, txt.length() - 1 );
		return txt;
	}

	EditText vistaEdita;
	void edita( final View vistaPezzo ) {
		final FloatingActionButton fab = findViewById( R.id.dettaglio_fab );
		final ActionBar barra = getSupportActionBar();

		// Termina l'eventuale editazione di un altro pezzo
		for( int i=0; i < box.getChildCount(); i++ ) {
			View altroPezzo = box.getChildAt(i);
			EditText vistaEdita = altroPezzo.findViewById( R.id.fatto_edita );
			if( vistaEdita != null ) {
				if( vistaEdita.isShown() ) {
					TextView vistaTesto = altroPezzo.findViewById( R.id.fatto_testo );
					if( !vistaEdita.getText().equals(vistaTesto.getText()) ) // se c'è stata editazione
						salva( altroPezzo, barra, fab );
					else
						ripristina( altroPezzo, barra, fab );
				}
			}
		}
		// Poi rende editabile questo pezzo
		final TextView vistaTesto = vistaPezzo.findViewById( R.id.fatto_testo );
		vistaTesto.setVisibility( View.GONE );
		vistaEdita = vistaPezzo.findViewById( R.id.fatto_edita );
		vistaEdita.setVisibility( View.VISIBLE );
		fab.hide();

		// Se è un luogo sostituisce vistaEdita con TrovaLuogo
		if( vistaPezzo.getTag(R.id.tag_oggetto).equals("Place") && !(vistaEdita instanceof TrovaLuogo) ) {
			ViewGroup parent = (ViewGroup) vistaPezzo;  // todo: si potrebbe usare direttamente vistaPezzo se fosse un ViewGroup o LinearLayout anzicé View
			int index = parent.indexOfChild( vistaEdita );
			parent.removeView( vistaEdita );
			vistaEdita = new TrovaLuogo( vistaEdita.getContext(), null );
			vistaEdita.setId( R.id.fatto_edita );
			vistaEdita.setText( vistaTesto.getText() );
			vistaEdita.setInputType( InputType.TYPE_TEXT_FLAG_CAP_WORDS );
			parent.addView( vistaEdita, index );
		}
		// Se non si tratta di una data mostra la tastiera
		if( !vistaPezzo.getTag(R.id.tag_oggetto).equals("Date") ) {
			InputMethodManager imm = (InputMethodManager) getSystemService( Context.INPUT_METHOD_SERVICE );
			imm.toggleSoftInput( InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY );
		}
		vistaEdita.requestFocus();
		vistaEdita.setSelection( vistaEdita.getText().length() );	// cursore alla fine

		// ActionBar personalizzata
		barra.setDisplayHomeAsUpEnabled( false );	// nasconde freccia <-
		qualeMenu = 0;
		invalidateOptionsMenu();
		View barraAzione = getLayoutInflater().inflate( R.layout.barra_edita, new LinearLayout(box.getContext()), false);
		barraAzione.findViewById(R.id.edita_annulla).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				vistaEdita.setText( vistaTesto.getText() );
				ripristina( vistaPezzo, barra, fab );
			}
		} );
		barraAzione.findViewById(R.id.edita_salva).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				//s.l( oggetto.getClass() +"  "+ vistaPezzo.getTag(R.id.tag_oggetto) );
				salva( vistaPezzo, barra, fab );
			}
		} );
		barra.setCustomView( barraAzione );
		barra.setDisplayShowCustomEnabled( true );
	}

	void salva( View vistaPezzo, ActionBar barra, FloatingActionButton fab ) {
		if( editoreData != null && editoreData.tipo==10 ) editoreData.genera( true ); // In sostanza solo per aggiungere le parentesi alla data frase
		String testo = vistaEdita.getText().toString();
		try {
			oggetto.getClass().getMethod( "set" + vistaPezzo.getTag(R.id.tag_oggetto), String.class )
					.invoke( oggetto, testo );
		} catch( Exception e ) { // NoSuchMethodException|IllegalAccessException|InvocationTargetException|SecurityException
			e.printStackTrace();
			Toast.makeText( box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			return;	// in caso di errore rimane in modalità editore
		}
		((TextView)vistaPezzo.findViewById( R.id.fatto_testo )).setText( testo );
		ripristina( vistaPezzo, barra, fab );
		U.salvaJson();
		Globale.editato = true; // quando torna indietro, Individuo o un altro Dettaglio aggiornano i contenuti
	}

	void ripristina( View vistaPezzo, ActionBar barra, FloatingActionButton fab ) {
		vistaEdita.setVisibility( View.GONE );
		vistaPezzo.findViewById( R.id.fatto_data ).setVisibility( View.GONE );
		vistaPezzo.findViewById( R.id.fatto_testo ).setVisibility( View.VISIBLE );
		barra.setDisplayShowCustomEnabled( false );	// nasconde barra personalizzata
		barra.setDisplayHomeAsUpEnabled( true );
		qualeMenu = 1;
		invalidateOptionsMenu();
		InputMethodManager imm = (InputMethodManager)getSystemService(getApplicationContext().INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow( vistaPezzo.getWindowToken(), 0 );
		fab.show();
	}

	// Menu opzioni
	int qualeMenu = 1;	// serve per nasconderlo quando si entra in modalità editore
	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		if( qualeMenu == 1 ) {	// Menu standard della barra
			menu.add( 0, 1, 0, R.string.delete );
		}
		return true;
	}
	@Override	// è evocato quando viene scelta una voce del menu E cliccando freccia indietro
	public boolean onOptionsItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 1 ) {
			// todo: conferma eliminazione di questo oggetto..
			elimina();
			U.salvaJson();
			Globale.editato = true;
			onBackPressed();
		} else {
			onBackPressed();
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		s.l( "onBackPressed" );
		if( oggetto instanceof EventFact )
			Evento.ripulisciTag( (EventFact)oggetto );
		super.onBackPressed();
	}

	public void elimina() {}

	// Menu contestuale
	View vistaPezzo;	// testo editabile, note, citazioni, media...
	Object oggettoPezzo;
	Person pers; // siccome usato molto ne facciamo un oggettoPezzo a sè stante
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {	// info è null
		if( qualeMenu != 0 ) {	// Se siamo in modalità edita mostra i menu editore
			vistaPezzo = vista;
			oggettoPezzo = vista.getTag( R.id.tag_oggetto );
			if( oggettoPezzo instanceof Person ) {
				pers = (Person) oggettoPezzo;
				Family fam = (Family) oggetto;
				menu.add(0, 10, 0, R.string.diagram );
				menu.add(0, 11, 0, R.string.card );
				if( !pers.getParentFamilies(gc).isEmpty() )
					if( !pers.getParentFamilies(gc).get(0).equals( fam ) )
						menu.add(0, 12, 0, R.string.family_as_child );
				if( !pers.getSpouseFamilies(gc).isEmpty() )
					if( !pers.getSpouseFamilies(gc).get(0).equals( fam ) )
						menu.add(0, 13, 0, R.string.family_as_spouse );
				if( fam.getChildren(gc).indexOf(pers) > 0 )
					menu.add( 0, 14, 0, R.string.move_up );
				if( fam.getChildren(gc).indexOf(pers) < fam.getChildren(gc).size()-1 && fam.getChildren(gc).indexOf(pers) >= 0 )
					// così esclude i genitori il cui indice è -1
					menu.add( 0, 15, 0, R.string.move_down );
				menu.add( 0, 16, 0, R.string.modify );
				menu.add( 0, 17, 0, R.string.unlink );
				menu.add( 0, 18, 0, R.string.delete );
			} else if( oggettoPezzo instanceof Note ) {
				if( ( (Note) oggettoPezzo ).getId() != null )
					menu.add( 0, 20, 0, R.string.unlink );
				menu.add( 0, 21, 0, R.string.delete );
			} else if( oggettoPezzo instanceof SourceCitation )
				menu.add( 0, 30, 0, R.string.delete );
			else if( oggettoPezzo instanceof Media ) {
				if( ( (Media) oggettoPezzo ).getId() != null )
					menu.add( 0, 40, 0, R.string.unlink );
				menu.add( 0, 41, 0, R.string.delete );
			} else if( oggettoPezzo instanceof Address )
				menu.add( 0, 50, 0, R.string.delete );
			else if( oggettoPezzo instanceof EventFact )
				menu.add( 0, 55, 0, R.string.delete );
			else if( oggettoPezzo instanceof GedcomTag )
				menu.add( 0, 60, 0, R.string.delete );
			else if( oggettoPezzo instanceof Source )
				menu.add( 0, 70, 0, R.string.choose_source );
			else if( oggettoPezzo instanceof RepositoryRef )
				menu.add( 0, 80, 0, R.string.delete );
			else if( oggettoPezzo instanceof Repository )
				menu.add( 0, 90, 0, R.string.choose_repository );
			else if( oggettoPezzo instanceof Integer ) {	// Immaginona
				if( oggettoPezzo.equals( 43614 ) )
					menu.add( 0, 100, 0, R.string.choose_file );
			} else if( oggettoPezzo instanceof String ) {
				//metodoPezzo = (String) vista.getTag();
				menu.add( 0, 0, 0, R.string.copy );
				menu.add( 0, 1, 0, R.string.delete );
			}
		}
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:	// Pezzo editabile
				ClipboardManager clipboard = (ClipboardManager) getSystemService(box.getContext().CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText(
						((TextView)vistaPezzo.findViewById( R.id.fatto_titolo )).getText(),
						((TextView)vistaPezzo.findViewById( R.id.fatto_testo )).getText() );
				clipboard.setPrimaryClip(clip);
				return true;
			case 1:
				// todo CONFERMA
				try {
					oggetto.getClass().getMethod( "set" + oggettoPezzo, String.class ).invoke( oggetto, (Object)null );
				} catch( NoSuchMethodException|IllegalAccessException|InvocationTargetException|SecurityException e ) {
					Toast.makeText( box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
					break;
				}
				//vistaPezzo.setVisibility( View.GONE );
				box.removeView( vistaPezzo );
				Globale.editato = true;
				break;
			case 10: // Diagramma
				Globale.individuo = pers.getId();
				startActivity( new Intent( this, Principe.class ) );
				return true;
			case 11: // Scheda persona
				Intent intento = new Intent( this, Individuo.class );
				intento.putExtra( "idIndividuo", pers.getId() );
				startActivity( intento );
				return true;
			case 12: // Famiglia come figlio
				Intent intent = new Intent( this, Famiglia.class );
				intent.putExtra( "idFamiglia", pers.getParentFamilies(gc).get(0).getId() );
				startActivity( intent );
				return true;
			case 13: // Famiglia come coniuge
				Intent inten = new Intent( this, Famiglia.class );
				inten.putExtra( "idFamiglia", pers.getSpouseFamilies(gc).get(0).getId() );
				startActivity( inten );
				return true;
			case 14: // Figlio sposta su
				Family fa = (Family) oggetto;
				ChildRef refBimbo = fa.getChildRefs().get( fa.getChildren(gc).indexOf(pers) );
				fa.getChildRefs().add( fa.getChildRefs().indexOf(refBimbo)-1, refBimbo );
				fa.getChildRefs().remove( fa.getChildRefs().lastIndexOf(refBimbo) );
				recreate();
				break;
			case 15: // Figlio sposta giù
				Family f = (Family) oggetto;
				ChildRef refBimb = f.getChildRefs().get( f.getChildren(gc).indexOf(pers) );
				f.getChildRefs().add( f.getChildRefs().indexOf(refBimb)+2, refBimb );
				f.getChildRefs().remove( f.getChildRefs().indexOf(refBimb) );
				recreate();
				break;
			case 16: // Modifica
				Intent i = new Intent( this, EditaIndividuo.class );
				i.putExtra( "idIndividuo", pers.getId() );
				startActivity( i );
				return true;
			case 17:	// Scollega
				Famiglia.scollega( pers.getId(), (Family)oggetto );
				box.removeView( vistaPezzo );
				break;
			case 18:
				Anagrafe.elimina( pers.getId(), this, vistaPezzo );
				break;
			case 20: 	// Nota
				U.scollegaNota( (Note)oggettoPezzo, oggetto, vistaPezzo );
				break;
			case 21:
				U.eliminaNota( (Note)oggettoPezzo, oggetto, vistaPezzo );
				break;
			case 30: 	// Citazione fonte
				// todo conferma
				((SourceCitationContainer)oggetto).getSourceCitations().remove( oggettoPezzo );
				box.removeView( vistaPezzo );
				break;
			case 40:	// Media
				Galleria.scollegaMedia( (Media)oggettoPezzo, oggetto, vistaPezzo );
				break;
			case 41:
				Galleria.eliminaMedia( (Media)oggettoPezzo, oggetto, vistaPezzo );
				break;
			case 50:	// Address
				((EventFact)oggetto).setAddress( null );
				box.removeView( vistaPezzo );
				break;
			case 55:    // Evento di Famiglia
				((Family)oggetto).getEventsFacts().remove( oggettoPezzo );
				box.removeView( vistaPezzo );
				break;
			case 60:	// Estensione
				U.eliminaEstensione( (GedcomTag)oggettoPezzo, oggetto, vistaPezzo );
				Globale.editato = true;
				break;
			case 70:	// Scegli fonte in Biblioteca
				Intent inte = new Intent( Dettaglio.this, Principe.class );
				inte.putExtra( "bibliotecaScegliFonte", true );
				startActivityForResult( inte,7047 );
				return true;
			case 80: 	// Citazione archivio
				// todo conferma
				((Source)oggetto).setRepositoryRef( null );
				box.removeView( vistaPezzo );
				break;
			case 90:	// Scegli archivio in Magazzino
				Intent intn = new Intent( Dettaglio.this, Principe.class );
				intn.putExtra( "magazzinoScegliArchivio", true );
				startActivityForResult( intn,5390 );
				return true;
			case 100:	// Immaginona scegli immagine
				Intent in = new Intent( Intent.ACTION_GET_CONTENT );
				//in.setType( "image/*" );
				in.setType( "*/*" );
				startActivityForResult( in,5173 );
				return true;
			default:
				return false;
		}
		U.salvaJson();
		return true;
	}
}
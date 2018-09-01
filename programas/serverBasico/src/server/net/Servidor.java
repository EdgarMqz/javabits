package server.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import server.conf.Configuracion;
import server.exception.ServerException;
import server.service.Servicio;

public class Servidor {

	private static final Logger log = LogManager.getLogger();

	private Configuracion conf;

	private ServerSocket srvSocket;
	private Thread hiloPrincipal;
	private volatile boolean running;

	public Servidor(Configuracion cfg) throws ServerException {
		this.conf = cfg;
	}

	public synchronized void comenzar() throws ServerException {
		if (hiloPrincipal != null)
			throw new IllegalStateException("El hilo principal est� corriendo");

		crearHiloPrincipal();
		crearHiloComando();
	}

	private void crearHiloComando() {
		try {
			ServerSocket srvComandos = new ServerSocket(conf.getPuertoComando());
			log.info("Se crea el socket servidor de comandos en el puerto " + conf.getPuertoComando());
			while (true) {
				Socket cltCmd = srvComandos.accept();
				BufferedReader bin = new BufferedReader(new InputStreamReader(cltCmd.getInputStream()));

				String linea = bin.readLine();
				log.info("Se recibi� el comando: " + linea);
				if (linea.indexOf(conf.getPalabraClaveTerminar()) > 0) {
					parar();
					break;
				}
				bin.close();
				cltCmd.close();
			}
			srvComandos.close();
			srvComandos = null;
		} catch (IOException e) {
			log.error("Ocurrio un error al leer el comando en el hilo de comando", e);
		}

	}

	private void crearHiloPrincipal() throws ServerException {
		try {
			srvSocket = new ServerSocket(conf.getPuerto());
			log.info("Se crea el socket servidor en el puerto " + conf.getPuerto());
		} catch (IOException e) {
			log.error("El puerto " + conf.getPuerto() + " ya esta ocupado o no se puede abrir", e);
			srvSocket = null;
		}

		if (srvSocket == null)
			throw new ServerException("No se puede abrir el puerto, no podemos comenzar");

		hiloPrincipal = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					running = true;
					while (running) {
						Socket clt = srvSocket.accept();
						darServicio(clt);
					}
				} catch (SocketException e) {
					log.error("No se pudo crear el socket", e);

				} catch (IOException e) {
					log.error("Otro error de IO", e);
				}
			}
		}, "Servidor");

		log.info("A punto de comenzar el servidor principal en el puerto " + conf.getPuerto());
		hiloPrincipal.start();
	}

	public synchronized void parar() {
		if (hiloPrincipal == null) {
			log.warn("El hilo principal del servidor ya esta parado");
			return;
		}

		log.info("Iniciando el proceso para parar el servidor");
		running = false;
		try {
			if (srvSocket != null) {
				log.debug("Parando el accept del hilo principal cerrando el srvsocket");
				srvSocket.close();
			}
		} catch (IOException e) {
			log.error("Error al cerrar el server socket pero de todas maneras se cierra", e);
		}

		hiloPrincipal = null;
		log.info("Hilo principal parado y no hay clientes corriendo");
	}

	protected void darServicio(Socket clt) {
		log.info("Recibe un nuevo cliente en el puerto cliente " + clt.getPort() + ":" + clt.getLocalPort());
		
		Servicio cltServicio = null;
		try {
			cltServicio = Servicio.construirServicio(conf.getServicio());
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e1) {
			log.error("Parece que la clase de la configuraci�n " + conf.getServicio() + " no esta igual al nombre de la clase", e1);
			return;
		}
		
		try {
			cltServicio.configurar(clt);
		} catch (IOException e) {
			log.error("No se puede configurar el servicio", e);
			return;
		}
		
		Thread worker = new Thread(cltServicio);
		worker.start();
	}
}

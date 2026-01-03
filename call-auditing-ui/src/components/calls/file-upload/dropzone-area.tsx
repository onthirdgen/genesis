/**
 * Dropzone Area Component
 *
 * Drag-and-drop file upload area with file validation
 */

import { useCallback } from 'react';
import { useDropzone, type FileRejection } from 'react-dropzone';
import { Upload, FileAudio } from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import { validateAudioFile } from '@/lib/utils/validation';

export interface DropzoneAreaProps {
  onFileAccepted: (file: File) => void;
  onFileRejected?: (errors: string[]) => void;
  disabled?: boolean;
  className?: string;
  maxSize?: number; // in bytes
}

export function DropzoneArea({
  onFileAccepted,
  onFileRejected,
  disabled = false,
  className,
  maxSize = 100 * 1024 * 1024, // 100MB default
}: DropzoneAreaProps) {
  const onDrop = useCallback(
    (acceptedFiles: File[], fileRejections: FileRejection[]) => {
      // Handle rejected files
      if (fileRejections.length > 0) {
        const errors = fileRejections.flatMap((rejection) =>
          rejection.errors.map((error) => error.message)
        );
        onFileRejected?.(errors);
        return;
      }

      // Get the first accepted file
      const file = acceptedFiles[0];
      if (!file) return;

      // Validate audio format and size
      const validationResult = validateAudioFile(file, { maxSize });

      if (!validationResult.valid) {
        onFileRejected?.(validationResult.errors);
        return;
      }

      // File is valid
      onFileAccepted(file);

      // Show warnings if any
      if (validationResult.warnings && validationResult.warnings.length > 0) {
        console.warn('File warnings:', validationResult.warnings);
      }
    },
    [onFileAccepted, onFileRejected, maxSize]
  );

  const {
    getRootProps,
    getInputProps,
    isDragActive,
    isDragReject,
    isDragAccept,
  } = useDropzone({
    onDrop,
    accept: {
      'audio/wav': ['.wav'],
      'audio/wave': ['.wav'],
      'audio/x-wav': ['.wav'],
      'audio/mp3': ['.mp3'],
      'audio/mpeg': ['.mp3'],
      'audio/mp4': ['.m4a', '.mp4'],
      'audio/x-m4a': ['.m4a'],
      'audio/flac': ['.flac'],
      'audio/x-flac': ['.flac'],
      'audio/ogg': ['.ogg'],
      'audio/webm': ['.webm'],
    },
    maxFiles: 1,
    maxSize,
    disabled,
    multiple: false,
  });

  return (
    <div
      {...getRootProps()}
      className={cn(
        'relative flex flex-col items-center justify-center w-full p-12 border-2 border-dashed rounded-lg cursor-pointer transition-all duration-200',
        'hover:border-primary/50 hover:bg-primary/5',
        'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2',
        isDragActive && 'border-primary bg-primary/10',
        isDragAccept && 'border-green-500 bg-green-50 dark:bg-green-950',
        isDragReject && 'border-red-500 bg-red-50 dark:bg-red-950',
        disabled && 'opacity-50 cursor-not-allowed hover:border-border hover:bg-transparent',
        className
      )}
      role="button"
      tabIndex={0}
      aria-label="Upload audio file"
    >
      <input {...getInputProps()} />

      {/* Icon */}
      <div className="mb-4">
        {isDragActive ? (
          <Upload
            className={cn(
              'h-12 w-12 transition-colors',
              isDragAccept && 'text-green-500',
              isDragReject && 'text-red-500',
              !isDragAccept && !isDragReject && 'text-primary'
            )}
          />
        ) : (
          <FileAudio className="h-12 w-12 text-muted-foreground" />
        )}
      </div>

      {/* Text */}
      <div className="text-center">
        {isDragActive ? (
          <p className="text-lg font-medium">
            {isDragAccept && 'Drop the audio file here'}
            {isDragReject && 'Only audio files are accepted'}
          </p>
        ) : (
          <>
            <p className="text-lg font-medium mb-1">
              Drag & drop an audio file here
            </p>
            <p className="text-sm text-muted-foreground mb-2">
              or click to browse
            </p>
            <p className="text-xs text-muted-foreground">
              Supports: WAV, MP3, M4A, FLAC, OGG, WEBM (max {Math.floor(maxSize / 1024 / 1024)}MB)
            </p>
          </>
        )}
      </div>

      {/* Keyboard hint */}
      <div className="absolute bottom-3 right-3">
        <kbd className="px-2 py-1 text-xs font-semibold text-muted-foreground bg-muted border border-border rounded">
          Enter/Space
        </kbd>
      </div>
    </div>
  );
}

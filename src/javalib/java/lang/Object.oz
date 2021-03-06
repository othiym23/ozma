functor

import
   OzmaRuntime('NewObject':NewObject
               'InitObject':InitObject
               'StringLiteral':StringLiteral) at 'x-ozma://system/OzmaRuntime.ozf'
   ObjectMonitor(newMonitor:NewMonitor) at 'x-ozma://system/ObjectMonitor.ozf'
   `functor:java.lang.Class`('type:java.lang.Class':Class
                             'class:java.lang.Class':ClassClass) at 'x-ozma://root/java/lang/Class.ozf'

export
   'type:java.lang.Object':Object
   'class:java.lang.Object':ObjectClass

define

   NextID = {NewCell 1}

   class Object from BaseObject
      attr
         identity
         'class'
         monitor

      meth !InitObject(ObjClass InitMessage)
         local ID Next in
            {Exchange NextID ID Next}
            Next = ID + 1
            identity := ID
         end

         'class' := ObjClass
         monitor := {ByNeed NewMonitor}
         {self InitMessage}
      end

      meth '<init>#1063877011'($)
         unit
      end

      meth '$getPublic$'(Field $)
         @Field
      end

      meth '$setPublic$'(Field Value)
         Field := Value
      end

      meth 'getClass#-530663260'($)
         @'class'
      end

      % For convenience in other Oz functors
      meth getClass($)
         {self 'getClass#-530663260'($)}
      end

      % In the JavaDoc, defined as:
      %   getClass().getName() + '@' + Integer.toHexString(hashCode())
      meth 'toString#1195259493'($)
         ClassName = {{@'class' 'getName#1195259493'($)} toRawString($)}
         HashCode = {self 'hashCode#-1882783961'($)}
      in
         {StringLiteral ClassName#'@'#HashCode}
      end

      % For convenience in other Oz functors
      meth toString($)
         {self 'toString#1195259493'($)}
      end

      meth identity($)
         @identity
      end

      meth 'hashCode#-1882783961'($)
         @identity
      end

      meth 'equals#-1875011758'(Other $)
         self == Other
      end

      meth synchronized(P $)
         {@monitor.'lock' P}
      end

      meth 'wait#1763596620'($)
         {@monitor.wait}
         unit
      end

      meth 'notify#1763596620'($)
         {@monitor.notify}
         unit
      end

      meth 'notifyAll#1763596620'($)
         {@monitor.notifyAll}
         unit
      end
   end

   ObjectClass = {ByNeed fun {$}
                            {NewObject Class ClassClass
                             '<init>'("java.lang.Object"
                                      unit
                                      nil
                                      nil
                                      _)}
                         end}

end
